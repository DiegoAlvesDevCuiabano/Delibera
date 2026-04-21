package com.delibera.controller;

import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoAcaoAuditoria;
import com.delibera.repository.EmailLogRepository;
import com.delibera.service.AnexoService;
import com.delibera.service.AuditLogService;
import com.delibera.service.EmailService;
import com.delibera.service.SolicitacaoService;
import com.delibera.service.UsuarioService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Controller
@RequestMapping("/coordenacao")
public class CoordenacaoController {

    private static final Logger logger = LoggerFactory.getLogger(CoordenacaoController.class);

    private final SolicitacaoService solicitacaoService;
    private final UsuarioService usuarioService;
    private final AnexoService anexoService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final EmailLogRepository emailLogRepository;

    public CoordenacaoController(SolicitacaoService solicitacaoService, UsuarioService usuarioService,
                                  AnexoService anexoService, EmailService emailService,
                                  AuditLogService auditLogService, EmailLogRepository emailLogRepository) {
        this.solicitacaoService = solicitacaoService;
        this.usuarioService = usuarioService;
        this.anexoService = anexoService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.emailLogRepository = emailLogRepository;
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Authentication auth, Model model) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Long instId = coord.getInstituicao().getId();

        long total = solicitacaoService.contarTotal(instId);
        long pendentes = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.EM_ANALISE)
                + solicitacaoService.contarPorStatus(instId, StatusSolicitacao.AGUARDANDO_PROFESSOR);
        long resolvidas = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.RESOLVIDO);
        long negadas = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.NEGADO);

        List<Solicitacao> pendentesList = solicitacaoService.listarPendentesPorInstituicao(instId);
        List<Solicitacao> todas = solicitacaoService.listarPorInstituicao(instId);

        model.addAttribute("coord", coord);
        model.addAttribute("total", total);
        model.addAttribute("pendentes", pendentes);
        model.addAttribute("resolvidas", resolvidas);
        model.addAttribute("negadas", negadas);
        model.addAttribute("pendentesList", pendentesList);
        model.addAttribute("todasSolicitacoes", todas);
        return "pages/coordenacao/dashboard";
    }

    @GetMapping("/solicitacoes/{id}")
    public String analisar(@PathVariable Long id, Authentication auth, Model model) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Solicitacao solicitacao = solicitacaoService.buscarPorId(id);
        solicitacaoService.verificarOwnership(solicitacao, coord);

        model.addAttribute("solicitacao", solicitacao);
        model.addAttribute("coord", coord);
        model.addAttribute("anexos", anexoService.listarPorSolicitacao(id));
        model.addAttribute("emailsEnviados", emailLogRepository.findBySolicitacaoIdOrderByEnviadoEmDesc(id));
        return "pages/coordenacao/analisar-solicitacao";
    }

    @PostMapping("/solicitacoes/{id}/deferir")
    public String deferir(@PathVariable Long id, @RequestParam String parecer,
                           Authentication auth, HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        try {
            Solicitacao s = solicitacaoService.deferir(id, parecer, coord);
            emailService.notificarMudancaStatus(s);
            auditLogService.registrar(coord, TipoAcaoAuditoria.MUDANCA_STATUS,
                    "SOLICITACAO", id, IpExtractor.getClientIp(request));
            redirectAttributes.addFlashAttribute("mensagem", "Solicitação deferida com sucesso.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro",
                    "Solicitação não encontrada ou você não tem permissão para esta ação.");
        }
        return "redirect:/coordenacao";
    }

    @PostMapping("/solicitacoes/{id}/encaminhar")
    public String encaminhar(@PathVariable Long id, @RequestParam String parecer,
                              Authentication auth, HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        try {
            Solicitacao s = solicitacaoService.encaminhar(id, parecer, coord);
            emailService.notificarMudancaStatus(s);
            auditLogService.registrar(coord, TipoAcaoAuditoria.MUDANCA_STATUS,
                    "SOLICITACAO", id, IpExtractor.getClientIp(request));
            redirectAttributes.addFlashAttribute("mensagem", "Solicitação encaminhada ao professor.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro",
                    "Solicitação não encontrada ou você não tem permissão para esta ação.");
        }
        return "redirect:/coordenacao";
    }

    @PostMapping("/solicitacoes/{id}/indeferir")
    public String indeferir(@PathVariable Long id, @RequestParam String parecer,
                             Authentication auth, HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        try {
            Solicitacao s = solicitacaoService.indeferir(id, parecer, coord);
            emailService.notificarMudancaStatus(s);
            auditLogService.registrar(coord, TipoAcaoAuditoria.MUDANCA_STATUS,
                    "SOLICITACAO", id, IpExtractor.getClientIp(request));
            redirectAttributes.addFlashAttribute("mensagem", "Solicitação indeferida.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro",
                    "Solicitação não encontrada ou você não tem permissão para esta ação.");
        }
        return "redirect:/coordenacao";
    }

    @GetMapping("/anexos/{id}/download")
    public ResponseEntity<InputStreamResource> downloadAnexo(
            @PathVariable Long id, Authentication auth, HttpServletRequest request) throws IOException {

        Usuario coord = usuarioService.buscarPorEmail(auth.getName());

        // Buscar anexo — retorna 404 se não existe
        Anexo anexo;
        try {
            anexo = anexoService.buscarPorId(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        // Ownership check — retorna 404 (não 403) para não confirmar existência
        Solicitacao solicitacao = anexo.getSolicitacao();
        if (!coord.isCrossTenant() && (coord.getInstituicao() == null
                || !solicitacao.getInstituicao().getId().equals(coord.getInstituicao().getId()))) {
            logger.warn("[ACESSO NEGADO] Usuario {} tentou baixar anexo {} de outra instituicao",
                    coord.getId(), id);
            return ResponseEntity.notFound().build();
        }

        // Verificar arquivo no disco
        Path arquivo = anexoService.getArquivo(anexo);
        if (!Files.exists(arquivo)) {
            logger.error("[DOWNLOAD] Arquivo fisico nao encontrado: {}", anexo.getNomeArmazenado());
            return ResponseEntity.notFound().build();
        }

        // Auditoria
        auditLogService.registrar(coord, TipoAcaoAuditoria.DOWNLOAD_ANEXO,
                "ANEXO", id, IpExtractor.getClientIp(request));

        // Sanitizar nome do arquivo para Content-Disposition (RFC 6266)
        String nomeOriginal = sanitizarFilename(anexo.getNomeOriginal());
        String encodedFilename = URLEncoder.encode(nomeOriginal, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nomeOriginal + "\"; filename*=UTF-8''" + encodedFilename)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(anexo.getContentType()))
                .contentLength(anexo.getTamanho())
                .body(new InputStreamResource(Files.newInputStream(arquivo)));
    }

    private String sanitizarFilename(String filename) {
        if (filename == null) return "download";
        // Remove caracteres perigosos para headers HTTP
        return filename.replaceAll("[\\r\\n\"\\\\]", "_");
    }
}
