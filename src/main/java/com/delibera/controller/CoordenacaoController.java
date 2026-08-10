package com.delibera.controller;

import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.ParecerProfessor;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoAcaoAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.delibera.repository.EmailLogRepository;
import com.delibera.repository.ParecerProfessorRepository;
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
import java.security.SecureRandom;
import java.util.List;

@Controller
@RequestMapping("/coordenacao")
public class CoordenacaoController {

    private static final Logger logger = LoggerFactory.getLogger(CoordenacaoController.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final java.time.format.DateTimeFormatter FMT_DATA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final java.time.format.DateTimeFormatter FMT_DATA_HORA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SolicitacaoService solicitacaoService;
    private final UsuarioService usuarioService;
    private final AnexoService anexoService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final EmailLogRepository emailLogRepository;
    private final ParecerProfessorRepository parecerProfessorRepository;

    public CoordenacaoController(SolicitacaoService solicitacaoService, UsuarioService usuarioService,
                                  AnexoService anexoService, EmailService emailService,
                                  AuditLogService auditLogService, EmailLogRepository emailLogRepository,
                                  ParecerProfessorRepository parecerProfessorRepository) {
        this.solicitacaoService = solicitacaoService;
        this.usuarioService = usuarioService;
        this.anexoService = anexoService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.emailLogRepository = emailLogRepository;
        this.parecerProfessorRepository = parecerProfessorRepository;
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Authentication auth, Model model,
                             @RequestParam(required = false) String busca,
                             @RequestParam(required = false) StatusSolicitacao statusFiltro,
                             @RequestParam(defaultValue = "0") int page) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        Long instId = coord.getInstituicao().getId();

        long total = solicitacaoService.contarTotal(instId);
        long pendentes = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.EM_ANALISE)
                + solicitacaoService.contarPorStatus(instId, StatusSolicitacao.AGUARDANDO_PROFESSOR);
        long resolvidas = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.RESOLVIDO);
        long negadas = solicitacaoService.contarPorStatus(instId, StatusSolicitacao.NEGADO);

        List<Solicitacao> pendentesList = solicitacaoService.listarPendentesPorInstituicao(instId);
        int pagina = Math.max(page, 0);
        Page<Solicitacao> todasPage = solicitacaoService.listarPorInstituicaoComFiltros(
                instId, busca, statusFiltro, PageRequest.of(pagina, 20));

        model.addAttribute("coord", coord);
        model.addAttribute("total", total);
        model.addAttribute("pendentes", pendentes);
        model.addAttribute("resolvidas", resolvidas);
        model.addAttribute("negadas", negadas);
        model.addAttribute("pendentesList", pendentesList);
        model.addAttribute("todasSolicitacoes", todasPage.getContent());
        model.addAttribute("paginaAtual", pagina);
        model.addAttribute("totalPaginas", todasPage.getTotalPages());
        model.addAttribute("totalElementos", todasPage.getTotalElements());
        model.addAttribute("busca", busca);
        model.addAttribute("statusFiltro", statusFiltro);
        model.addAttribute("statusOptions", StatusSolicitacao.values());

        // Feedback popup: mostrar no dia ~15 do piloto (entre dia 12 e 18), se não respondeu ainda
        Instituicao instFeedback = coord.getInstituicao();
        boolean mostrarFeedback = false;
        if (instFeedback != null && instFeedback.getPilotoExpiraEm() != null && !instFeedback.isFeedbackRespondido()) {
            long diasDesdeInicio = java.time.Duration.between(
                    instFeedback.getPilotoExpiraEm().minusDays(30), // data de início = expiração - 30
                    java.time.LocalDateTime.now()
            ).toDays();
            mostrarFeedback = diasDesdeInicio >= 12 && diasDesdeInicio <= 18;
        }
        model.addAttribute("mostrarFeedback", mostrarFeedback);
        model.addAttribute("tempoMedioResolucao", solicitacaoService.calcularTempoMedioResolucaoDias(instId));
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
        model.addAttribute("pareceresProfessor", parecerProfessorRepository.findBySolicitacaoIdOrderByCriadoEmDesc(id));
        return "pages/coordenacao/analisar-solicitacao";
    }

    @PostMapping("/solicitacoes/{id}/deferir")
    public String deferir(@PathVariable Long id, @RequestParam String parecer,
                           @RequestParam(required = false) String parecerInterno,
                           Authentication auth, HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        try {
            Solicitacao s = solicitacaoService.deferir(id, parecer, parecerInterno, coord);
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
    public String encaminhar(@PathVariable Long id,
                              @RequestParam String parecer,
                              @RequestParam String emailProfessor,
                              Authentication auth, HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        try {
            Solicitacao s = solicitacaoService.encaminhar(id, parecer, coord);

            // Generate access code
            String codigo = gerarCodigoProfessor();

            // Create ParecerProfessor
            ParecerProfessor pp = new ParecerProfessor();
            pp.setSolicitacao(s);
            pp.setEmailProfessor(emailProfessor.trim().toLowerCase());
            pp.setCodigoAcesso(codigo);
            pp.setComentarioCoordenador(parecer);
            parecerProfessorRepository.save(pp);

            // Notify student of status change
            emailService.notificarMudancaStatus(s);

            // Notify professor with access code
            emailService.notificarProfessor(s, emailProfessor.trim(), codigo, parecer);

            auditLogService.registrar(coord, TipoAcaoAuditoria.MUDANCA_STATUS,
                    "SOLICITACAO", id, IpExtractor.getClientIp(request));
            redirectAttributes.addFlashAttribute("mensagem",
                    "Solicitação encaminhada para " + emailProfessor.trim());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro",
                    "Solicitação não encontrada ou você não tem permissão para esta ação.");
        }
        return "redirect:/coordenacao";
    }

    private String gerarCodigoProfessor() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(BASE36_CHARS.charAt(SECURE_RANDOM.nextInt(36)));
        }
        return "PRF-" + sb;
    }

    @PostMapping("/solicitacoes/{id}/indeferir")
    public String indeferir(@PathVariable Long id, @RequestParam String parecer,
                             @RequestParam(required = false) String parecerInterno,
                             Authentication auth, HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        try {
            Solicitacao s = solicitacaoService.indeferir(id, parecer, parecerInterno, coord);
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

    /**
     * Exportação do período em CSV. É o artefato que a coordenação leva para a direção
     * ou para o colegiado — sem ele, o valor entregue fica invisível na hora de renovar.
     *
     * <p>Não inclui `parecerInterno`: é anotação de trabalho e sairia da ferramenta
     * junto com o resto numa planilha que circula por email.
     */
    @GetMapping(value = "/relatorio.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> relatorioCsv(Authentication auth, HttpServletRequest request) {
        Usuario coord = usuarioService.buscarPorEmail(auth.getName());
        if (coord.getInstituicao() == null) {
            return ResponseEntity.badRequest().build();
        }

        List<Solicitacao> solicitacoes =
                solicitacaoService.listarPorInstituicao(coord.getInstituicao().getId());

        StringBuilder csv = new StringBuilder();
        // BOM para o Excel abrir UTF-8 sem quebrar acentuação.
        csv.append('﻿');
        csv.append("Protocolo;Tipo;Status;Aluno;Email;Curso;Turma;Disciplina;Professor;")
           .append("Data da ocorrencia;Criado em;Atualizado em;Dias ate a decisao;Parecer\n");

        for (Solicitacao s : solicitacoes) {
            csv.append(csvCampo(s.getProtocolo())).append(';')
               .append(csvCampo(s.getTipo() != null ? s.getTipo().getDescricao() : null)).append(';')
               .append(csvCampo(s.getStatus() != null ? s.getStatus().getDescricao() : null)).append(';')
               .append(csvCampo(s.getAlunoNome())).append(';')
               .append(csvCampo(s.getAlunoEmail())).append(';')
               .append(csvCampo(s.getAlunoCurso())).append(';')
               .append(csvCampo(s.getAlunoTurma())).append(';')
               .append(csvCampo(s.getDisciplina())).append(';')
               .append(csvCampo(s.getProfessor())).append(';')
               .append(csvCampo(s.getDataOcorrencia() != null ? s.getDataOcorrencia().format(FMT_DATA) : null)).append(';')
               .append(csvCampo(s.getCriadoEm() != null ? s.getCriadoEm().format(FMT_DATA_HORA) : null)).append(';')
               .append(csvCampo(s.getAtualizadoEm() != null ? s.getAtualizadoEm().format(FMT_DATA_HORA) : null)).append(';')
               .append(csvCampo(diasAteDecisao(s))).append(';')
               .append(csvCampo(s.getParecer())).append('\n');
        }

        auditLogService.registrar(coord, TipoAcaoAuditoria.EXPORTACAO_DADOS,
                "RELATORIO", coord.getInstituicao().getId(), IpExtractor.getClientIp(request));

        String arquivo = "delibera-solicitacoes-"
                + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arquivo + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String diasAteDecisao(Solicitacao s) {
        boolean decidida = s.getStatus() == StatusSolicitacao.RESOLVIDO
                || s.getStatus() == StatusSolicitacao.NEGADO;
        if (!decidida || s.getCriadoEm() == null || s.getAtualizadoEm() == null) {
            return "";
        }
        long horas = java.time.Duration.between(s.getCriadoEm(), s.getAtualizadoEm()).toHours();
        return String.valueOf(Math.round(horas / 24.0 * 10) / 10.0).replace('.', ',');
    }

    /**
     * Escapa o campo para CSV e neutraliza fórmulas: uma célula iniciada por
     * {@code = + - @} é executada pelo Excel ao abrir o arquivo, e o conteúdo aqui
     * vem de texto livre digitado por aluno anônimo.
     */
    private String csvCampo(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String limpo = valor.replace("\r", " ").replace("\n", " ");
        if ("=+-@".indexOf(limpo.charAt(0)) >= 0) {
            limpo = "'" + limpo;
        }
        return "\"" + limpo.replace("\"", "\"\"") + "\"";
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
