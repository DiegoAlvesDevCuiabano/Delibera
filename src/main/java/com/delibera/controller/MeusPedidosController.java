package com.delibera.controller;

import com.delibera.config.GlobalRateLimiter;
import com.delibera.model.dto.ExportacaoAlunoDTO;
import com.delibera.model.entity.Solicitacao;
import com.delibera.service.AcessoAlunoService;
import com.delibera.service.AnexoService;
import com.delibera.service.EmailService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Área do aluno, sem conta, acessada por link assinado enviado por email.
 *
 * <p>Cobre o que faltava no fluxo público: lista das próprias solicitações,
 * recuperação de protocolo esquecido, detalhe que sobrevive a refresh, exportação
 * dos dados e pedido de exclusão.
 */
@Controller
public class MeusPedidosController {

    private static final Logger logger = LoggerFactory.getLogger(MeusPedidosController.class);
    private static final DateTimeFormatter FMT_ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AcessoAlunoService acessoAluno;
    private final AnexoService anexoService;
    private final EmailService emailService;
    private final GlobalRateLimiter rateLimiter;

    public MeusPedidosController(AcessoAlunoService acessoAluno,
                                  AnexoService anexoService,
                                  EmailService emailService,
                                  GlobalRateLimiter rateLimiter) {
        this.acessoAluno = acessoAluno;
        this.anexoService = anexoService;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
    }

    // ── Pedir o link ─────────────────────────────────────────────

    @GetMapping("/acompanhar")
    public String formulario() {
        return "pages/aluno/consultar-protocolo";
    }

    /**
     * Envia o link de acesso. A resposta é sempre a mesma, exista ou não solicitação
     * para o email — caso contrário este endpoint viraria um oráculo para descobrir
     * quem já protocolou pedido na instituição.
     */
    @PostMapping("/acompanhar")
    public String enviarLink(@RequestParam String email, HttpServletRequest request, Model model) {
        String ip = IpExtractor.getClientIp(request);
        String normalizado = email == null ? "" : email.trim().toLowerCase();

        List<Solicitacao> encontradas = acessoAluno.listarPorEmail(normalizado);
        if (encontradas.isEmpty()) {
            // Conta como falha: sem isso, dá para varrer emails sem limite.
            rateLimiter.registrarFalha(ip, "/acompanhar");
            logger.info("[ACOMPANHAR] Nenhuma solicitação para o email consultado");
        } else {
            emailService.notificarLinkAcesso(normalizado, acessoAluno.gerarToken(normalizado),
                    encontradas.size());
        }

        model.addAttribute("enviado", true);
        model.addAttribute("email", normalizado);
        return "pages/aluno/consultar-protocolo";
    }

    // ── Lista ────────────────────────────────────────────────────

    @GetMapping("/meus-pedidos")
    public String listar(@RequestParam(name = "t", required = false) String token, Model model) {
        String email = acessoAluno.emailDoToken(token);
        if (email == null) {
            model.addAttribute("erroLink", true);
            return "pages/aluno/consultar-protocolo";
        }

        model.addAttribute("solicitacoes", acessoAluno.listarPorEmail(email));
        model.addAttribute("emAndamento", acessoAluno.contarEmAndamento(email));
        model.addAttribute("email", email);
        model.addAttribute("token", token);
        return "pages/aluno/acompanhar";
    }

    // ── Detalhe (sobrevive a F5 e pode ser salvo nos favoritos) ──

    @GetMapping("/meus-pedidos/{protocolo}")
    public String detalhe(@PathVariable String protocolo,
                           @RequestParam(name = "t", required = false) String token,
                           Model model) {
        String email = acessoAluno.emailDoToken(token);
        if (email == null) {
            model.addAttribute("erroLink", true);
            return "pages/aluno/consultar-protocolo";
        }

        try {
            Solicitacao s = acessoAluno.buscarDoAluno(protocolo, email);
            model.addAttribute("status", new com.delibera.model.dto.StatusPublicoDTO(s));
            model.addAttribute("recemCriada", false);
            model.addAttribute("token", token);
            return "pages/aluno/status-solicitacao";
        } catch (IllegalArgumentException e) {
            model.addAttribute("erroLink", true);
            return "pages/aluno/consultar-protocolo";
        }
    }

    // ── Portabilidade (LGPD art. 18, V) ──────────────────────────
    // Fora de /meus-pedidos/** de propósito: sob aquele prefixo elas colidiriam com
    // /meus-pedidos/{protocolo} e "exportar" seria lido como número de protocolo.

    @GetMapping("/meus-dados/exportar")
    @ResponseBody
    public ResponseEntity<ExportacaoAlunoDTO> exportar(
            @RequestParam(name = "t", required = false) String token) {

        String email = acessoAluno.emailDoToken(token);
        if (email == null) {
            return ResponseEntity.status(403).build();
        }

        // Montado dentro da transação do service — ver AcessoAlunoService.exportar.
        ExportacaoAlunoDTO exportacao =
                acessoAluno.exportar(email, anexoService::listarPorSolicitacao);

        String arquivo = "delibera-meus-dados-"
                + java.time.LocalDate.now().format(FMT_ARQUIVO) + ".json";

        logger.info("[LGPD] Exportação de dados gerada a pedido do titular");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + arquivo + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(exportacao);
    }

    // ── Eliminação (LGPD art. 18, VI) ────────────────────────────

    @PostMapping("/meus-dados/excluir")
    public String excluir(@RequestParam(name = "t", required = false) String token, Model model) {
        String email = acessoAluno.emailDoToken(token);
        if (email == null) {
            model.addAttribute("erroLink", true);
            return "pages/aluno/consultar-protocolo";
        }

        long emAndamento = acessoAluno.contarEmAndamento(email);
        int marcadas = acessoAluno.solicitarExclusao(email);

        model.addAttribute("solicitacoes", acessoAluno.listarPorEmail(email));
        model.addAttribute("emAndamento", emAndamento);
        model.addAttribute("email", email);
        model.addAttribute("token", token);
        model.addAttribute("mensagem", montarMensagemExclusao(marcadas, emAndamento));
        return "pages/aluno/acompanhar";
    }

    private String montarMensagemExclusao(int marcadas, long emAndamento) {
        if (marcadas == 0 && emAndamento > 0) {
            return "Nenhuma solicitação pôde ser excluída agora: as suas ainda estão em andamento. "
                    + "Cancele ou aguarde a decisão da coordenação e refaça o pedido.";
        }
        if (marcadas == 0) {
            return "Não há solicitações concluídas para excluir.";
        }
        String base = marcadas + (marcadas == 1
                ? " solicitação foi marcada para exclusão e será anonimizada em até 90 dias."
                : " solicitações foram marcadas para exclusão e serão anonimizadas em até 90 dias.");
        return emAndamento > 0
                ? base + " " + emAndamento + " em andamento ficaram de fora até a coordenação decidir."
                : base;
    }
}
