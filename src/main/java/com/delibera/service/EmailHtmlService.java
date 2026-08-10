package com.delibera.service;

import com.delibera.controller.VoucherController;
import com.delibera.model.entity.EmailLog;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.repository.EmailLogRepository;
import com.delibera.util.TokenAssinado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

/**
 * Service para envio de emails HTML com branding Delibera.
 * Usado pelo PilotoScheduledJob e pode ser usado para emails transacionais futuros.
 */
@Service
public class EmailHtmlService {

    private static final Logger logger = LoggerFactory.getLogger(EmailHtmlService.class);
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Prazo do link do voucher — o texto do email promete 7 dias. */
    private static final int VALIDADE_VOUCHER_DIAS = 7;

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final AlertaErroService alertaErroService;
    private final TokenAssinado tokenAssinado;
    private final boolean emailHabilitado;

    @Value("${delibera.email.from:contato@appdelibera.com.br}")
    private String remetente;

    @Value("${delibera.base-url:https://appdelibera.com.br}")
    private String baseUrl;

    @Value("${delibera.ambiente:}")
    private String ambiente;

    public EmailHtmlService(JavaMailSender mailSender,
                             EmailLogRepository emailLogRepository,
                             AlertaErroService alertaErroService,
                             TokenAssinado tokenAssinado,
                             @Value("${delibera.email.enabled:false}") boolean emailHabilitado) {
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.alertaErroService = alertaErroService;
        this.tokenAssinado = tokenAssinado;
        this.emailHabilitado = emailHabilitado;
    }

    private String prefixarAssunto(String assunto) {
        if (ambiente != null && !ambiente.isBlank() && !"PROD".equalsIgnoreCase(ambiente)) {
            return "[" + ambiente.toUpperCase() + "] " + assunto;
        }
        return assunto;
    }

    // ── Emails de retenção (piloto) ──────────────────────────────

    /** Email para quem usou o piloto e expirou — oferece voucher de +30 dias */
    public void enviarVoucherExtensao(Instituicao inst) {
        String assunto = "Delibera — 30 dias foi pouco? Temos um presente para você";
        String html = montarEmailHtml(
                "30 dias foi pouco?",
                "Notamos que você utilizou o Delibera durante o período de experiência. "
                + "Sabemos que avaliar uma ferramenta nova leva tempo — por isso, estamos "
                + "oferecendo mais 30 dias gratuitos para você continuar explorando.",
                "Ativar voucher de extensão",
                baseUrl + "/voucher/ativar?t="
                        + tokenAssinado.gerar(VoucherController.RECURSO, inst.getId(), VALIDADE_VOUCHER_DIAS),
                "Este voucher é válido por 7 dias. Após ativado, você terá mais 30 dias de acesso completo."
        );
        enviarHtml(null, inst.getDominio() != null ? "contato@" + inst.getDominio() : remetente,
                assunto, html, inst.getNome());
    }

    /** Email para quem cadastrou mas não usou — prazo maior, tenta atrair */
    public void enviarConviteRetorno(Instituicao inst) {
        String assunto = "Delibera — Sua conta está pronta e esperando por você";
        String html = montarEmailHtml(
                "Ainda não experimentou?",
                "Sua instituição foi cadastrada no Delibera mas não registrou nenhuma solicitação ainda. "
                + "Sabemos que a rotina da coordenação é corrida — por isso, estendemos seu acesso "
                + "por mais 45 dias para você testar com calma.",
                "Acessar o Delibera",
                baseUrl + "/login",
                "Precisa de ajuda para configurar? Responda este email e nosso time te orienta."
        );
        enviarHtml(null, inst.getDominio() != null ? "contato@" + inst.getDominio() : remetente,
                assunto, html, inst.getNome());
    }

    /** Email após voucher expirar — direciona para contato com o time */
    public void enviarContatoFinal(Instituicao inst) {
        String assunto = "Delibera — Seu período de avaliação encerrou";
        String html = montarEmailHtml(
                "Seu período de avaliação encerrou",
                "O acesso da " + inst.getNome() + " ao Delibera expirou. "
                + "Seus dados estão preservados e seguros. Para continuar usando a plataforma, "
                + "entre em contato com nosso time para ativar sua assinatura.",
                "Falar com o time",
                baseUrl + "/suporte",
                "Seus dados ficam preservados por 90 dias. Após esse período, serão anonimizados conforme nossa política de privacidade."
        );
        enviarHtml(null, inst.getDominio() != null ? "contato@" + inst.getDominio() : remetente,
                assunto, html, inst.getNome());
    }

    /** Email de feedback no dia ~15 do piloto */
    public void enviarFeedbackDia15(Instituicao inst) {
        String assunto = "Delibera — Como está sendo a experiência?";
        String html = montarEmailHtml(
                "Como está sendo a experiência?",
                "Você está usando o Delibera há cerca de 15 dias. "
                + "Gostaríamos muito de saber como está sendo — seu feedback "
                + "nos ajuda a melhorar a plataforma para você e para outras instituições.",
                "Responder pesquisa rápida",
                baseUrl + "/login",
                "A pesquisa aparece automaticamente no seu painel. São apenas 2 cliques."
        );
        enviarHtml(null, inst.getDominio() != null ? "contato@" + inst.getDominio() : remetente,
                assunto, html, inst.getNome());
    }

    // ── Suporte (formulário de contato) ────────────────────────

    /** Envia mensagem do formulário de suporte para contato@appdelibera.com.br */
    @Async
    public void enviarContatoSuporte(String nome, String emailRemetente, String assunto, String mensagem) {
        // Tudo aqui vem de um formulário anônimo e o corpo é enviado como HTML.
        // Sem escapar, qualquer visitante injeta markup na caixa de quem opera o
        // produto — link de phishing, imagem rastreadora, conteúdo forjado.
        String nomeSeguro = escaparHtml(nome);
        String emailSeguro = escaparHtml(emailRemetente);
        String assuntoSeguro = escaparHtml(assunto);
        String mensagemSegura = escaparHtml(mensagem);

        String assuntoEmail = "Delibera Suporte — [" + assunto + "] de " + nome;
        String html = montarEmailHtml(
                "Nova mensagem de suporte",
                "<p><strong>De:</strong> " + nomeSeguro + " (" + emailSeguro + ")</p>"
                + "<p><strong>Assunto:</strong> " + assuntoSeguro + "</p>"
                + "<div style='background:#f1f5f9;border-radius:8px;padding:1rem 1.25rem;margin:1rem 0;border-left:3px solid #f97316;'>"
                + "<p style='margin:0;white-space:pre-wrap;'>" + mensagemSegura + "</p></div>"
                + "<p style='font-size:0.85rem;color:#64748b;'>Responda diretamente para <strong>" + emailSeguro + "</strong></p>",
                null, null,
                null
        );
        enviarHtml(null, remetente, assuntoEmail, html, nome);
    }

    /** Escapa os cinco caracteres que mudam a estrutura do HTML. */
    static String escaparHtml(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    // ── Emails transacionais HTML (para uso futuro) ─────────────

    @Async
    public void enviarSolicitacaoCriadaHtml(Solicitacao s) {
        String assunto = "Delibera — Solicitação registrada: " + s.getProtocolo();
        String html = montarEmailHtml(
                "Solicitação registrada!",
                "<div style='text-align:center;background:#1e3a5f;color:white;padding:1.25rem;border-radius:10px;margin:1rem 0;'>"
                + "<div style='font-size:0.7rem;text-transform:uppercase;letter-spacing:2px;opacity:0.7;'>Seu protocolo</div>"
                + "<div style='font-size:1.8rem;font-weight:800;letter-spacing:2px;margin:0.25rem 0;'>" + s.getProtocolo() + "</div>"
                + "</div>"
                + "<p>Olá, <strong>" + s.getAlunoNome().split(" ")[0] + "</strong>! Sua solicitação de <strong>"
                + s.getTipo().getDescricao() + "</strong> para a disciplina <strong>" + s.getDisciplina()
                + "</strong> foi registrada com sucesso.</p>"
                + "<p>Guarde o número do protocolo acima — ele é sua chave de acompanhamento. "
                + "Você receberá atualizações por email a cada mudança de status.</p>",
                "Consultar status",
                baseUrl + "/consultar-protocolo",
                "Resposta esperada em até 3 dias úteis."
        );
        enviarHtml(s, s.getAlunoEmail(), assunto, html, s.getAlunoNome().split(" ")[0]);
    }

    // ── Template base HTML ──────────────────────────────────────

    private String montarEmailHtml(String titulo, String conteudo, String ctaTexto, String ctaUrl, String nota) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:0;background:#f8fafc;font-family:Arial,Helvetica,sans-serif;'>"
                + "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f8fafc;padding:2rem 0;'>"
                + "<tr><td align='center'>"
                + "<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.04);'>"

                // Header com logo (4 barras)
                + "<tr><td style='background:#1e3a5f;padding:1.5rem 2rem;text-align:center;'>"
                + "<table cellpadding='0' cellspacing='0' style='display:inline-block;'><tr>"
                + "<td style='vertical-align:middle;padding-right:10px;'>"
                + "<table cellpadding='0' cellspacing='0' style='width:28px;height:28px;background:white;border-radius:6px;'>"
                + "<tr><td style='padding:4px 5px;line-height:0;'>"
                + "<div style='width:12px;height:3px;background:#f97316;border-radius:1px;margin-bottom:2px;'></div>"
                + "<div style='width:18px;height:3px;background:#3b82f6;border-radius:1px;margin-bottom:2px;'></div>"
                + "<div style='width:18px;height:3px;background:#10b981;border-radius:1px;margin-bottom:2px;'></div>"
                + "<div style='width:10px;height:3px;background:#1e3a5f;border-radius:1px;'></div>"
                + "</td></tr></table></td>"
                + "<td style='vertical-align:middle;'>"
                + "<span style='font-family:Georgia,serif;font-style:italic;font-weight:bold;font-size:1.3rem;color:white;letter-spacing:-0.5px;'>"
                + "Delibera<span style='color:#f97316;font-style:normal;'>.</span></span></td>"
                + "</tr></table>"
                + "</td></tr>"

                // Barra laranja
                + "<tr><td style='background:#f97316;height:4px;'></td></tr>"

                // Conteúdo
                + "<tr><td style='padding:2rem 2.5rem;'>"
                + "<h1 style='font-size:1.4rem;color:#1e3a5f;margin:0 0 1rem;font-weight:700;'>" + titulo + "</h1>"
                + "<div style='font-size:0.95rem;color:#334155;line-height:1.7;'>" + conteudo + "</div>"

                // CTA Button
                + (ctaTexto != null ? "<div style='text-align:center;margin:1.75rem 0;'>"
                + "<a href='" + ctaUrl + "' style='display:inline-block;background:#f97316;color:white;padding:0.75rem 2rem;"
                + "border-radius:10px;text-decoration:none;font-weight:700;font-size:0.95rem;"
                + "box-shadow:0 4px 14px rgba(249,115,22,0.25);'>" + ctaTexto + "</a></div>" : "")

                // Nota
                + (nota != null ? "<p style='font-size:0.82rem;color:#64748b;margin:1rem 0 0;line-height:1.5;'>" + nota + "</p>" : "")
                + "</td></tr>"

                // Footer
                + "<tr><td style='background:#f1f5f9;padding:1.25rem 2rem;border-top:1px solid #e2e8f0;'>"
                + "<p style='margin:0;font-size:0.75rem;color:#64748b;text-align:center;line-height:1.6;'>"
                + "Delibera — Gestão de Solicitações Acadêmicas<br>"
                + "<span style='font-family:Georgia,serif;font-style:italic;color:#94a3b8;'>Deliberando com você, liberando seu tempo.</span><br>"
                + "<a href='" + baseUrl + "/termos' style='color:#64748b;text-decoration:underline;'>Termos</a> · "
                + "<a href='" + baseUrl + "/privacidade' style='color:#64748b;text-decoration:underline;'>Privacidade</a>"
                + "</p></td></tr>"

                + "</table></td></tr></table></body></html>";
    }

    // ── Envio HTML ──────────────────────────────────────────────

    private void enviarHtml(Solicitacao solicitacao, String para, String assunto, String html, String contexto) {
        assunto = prefixarAssunto(assunto);
        EmailLog log = new EmailLog();
        log.setSolicitacao(solicitacao);
        log.setDestinatario(para);
        log.setAssunto(assunto);
        log.setCorpo("[HTML] " + assunto); // salva referência, não o HTML todo

        if (!emailHabilitado) {
            log.setStatus("SIMULADO");
            emailLogRepository.save(log);
            logger.info("[EMAIL HTML SIMULADO] Para: {} | Assunto: {} | Contexto: {}", para, assunto, contexto);
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(html, true); // true = HTML
            mailSender.send(msg);

            log.setStatus("ENVIADO");
            emailLogRepository.save(log);
            logger.info("[EMAIL HTML ENVIADO] Para: {} | Assunto: {}", para, assunto);
        } catch (MessagingException e) {
            log.setStatus("FALHOU");
            log.setErro(e.getMessage());
            emailLogRepository.save(log);
            logger.error("[EMAIL HTML ERRO] Para: {} | Erro: {}", para, e.getMessage());
            alertaErroService.alertarFalhaEmail(log.getId(), para, assunto, e.getMessage());
        }
    }
}
