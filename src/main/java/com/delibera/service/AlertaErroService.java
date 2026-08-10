package com.delibera.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Envia alertas de erro diretamente para o admin.
 * NÃO usa EmailLog (evita loop se o próprio log falhar).
 * NÃO usa EmailHtmlService (evita dependência circular).
 */
@Service
public class AlertaErroService {

    private static final Logger logger = LoggerFactory.getLogger(AlertaErroService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final JavaMailSender mailSender;
    private final boolean emailHabilitado;

    @Value("${delibera.email.from:contato@appdelibera.com.br}")
    private String remetente;

    @Value("${delibera.ambiente:}")
    private String ambiente;

    public AlertaErroService(JavaMailSender mailSender,
                              @Value("${delibera.email.enabled:false}") boolean emailHabilitado) {
        this.mailSender = mailSender;
        this.emailHabilitado = emailHabilitado;
    }

    /**
     * Envia alerta de falha no envio de email.
     * Chamado quando um email transacional/HTML falha.
     */
    @Async
    public void alertarFalhaEmail(Long emailLogId, String destinatario, String assunto, String erro) {
        String prefixo = (ambiente != null && !ambiente.isBlank() && !"PROD".equalsIgnoreCase(ambiente))
                ? "[" + ambiente.toUpperCase() + "] " : "";

        String assuntoAlerta = prefixo + "⚠ ALERTA — Falha no envio de email";
        String corpo = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:0;background:#f8fafc;font-family:Arial,sans-serif;'>"
                + "<table width='100%' cellpadding='0' cellspacing='0' style='padding:2rem;'>"
                + "<tr><td align='center'>"
                + "<table width='560' cellpadding='0' cellspacing='0' style='background:#fff;border-radius:10px;border:2px solid #ef4444;'>"
                + "<tr><td style='background:#ef4444;padding:1rem 1.5rem;color:white;font-weight:bold;font-size:1rem;border-radius:8px 8px 0 0;'>"
                + "⚠ Falha no envio de email</td></tr>"
                + "<tr><td style='padding:1.5rem;'>"
                + "<table style='width:100%;font-size:0.9rem;color:#334155;'>"
                + "<tr><td style='padding:0.4rem 0;font-weight:600;color:#64748b;width:120px;'>EmailLog ID</td>"
                + "<td style='padding:0.4rem 0;'>" + (emailLogId != null ? emailLogId : "N/A") + "</td></tr>"
                + "<tr><td style='padding:0.4rem 0;font-weight:600;color:#64748b;'>Destinatário</td>"
                + "<td style='padding:0.4rem 0;'>" + destinatario + "</td></tr>"
                + "<tr><td style='padding:0.4rem 0;font-weight:600;color:#64748b;'>Assunto</td>"
                + "<td style='padding:0.4rem 0;'>" + assunto + "</td></tr>"
                + "<tr><td style='padding:0.4rem 0;font-weight:600;color:#64748b;'>Erro</td>"
                + "<td style='padding:0.4rem 0;color:#ef4444;font-weight:600;'>" + erro + "</td></tr>"
                + "<tr><td style='padding:0.4rem 0;font-weight:600;color:#64748b;'>Data/hora</td>"
                + "<td style='padding:0.4rem 0;'>" + LocalDateTime.now().format(FMT) + "</td></tr>"
                + "<tr><td style='padding:0.4rem 0;font-weight:600;color:#64748b;'>Ambiente</td>"
                + "<td style='padding:0.4rem 0;'>" + (ambiente != null ? ambiente : "N/A") + "</td></tr>"
                + "</table>"
                + "<p style='margin:1rem 0 0;font-size:0.82rem;color:#64748b;'>Verifique a tabela email_log no banco para mais detalhes.</p>"
                + "</td></tr></table>"
                + "</td></tr></table></body></html>";

        if (!emailHabilitado) {
            logger.warn("[ALERTA ERRO SIMULADO] EmailLog={} | Para: {} | Erro: {}", emailLogId, destinatario, erro);
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(remetente); // envia pra si mesmo (admin)
            helper.setSubject(assuntoAlerta);
            helper.setText(corpo, true);
            mailSender.send(msg);
            logger.info("[ALERTA ERRO ENVIADO] EmailLog={} | Erro original: {}", emailLogId, erro);
        } catch (Exception e) {
            // Se nem o alerta consegue enviar, só loga — não entra em loop
            logger.error("[ALERTA ERRO FALHOU] Não foi possível enviar alerta. EmailLog={} | Erro original: {} | Erro do alerta: {}",
                    emailLogId, erro, e.getMessage());
        }
    }

    /**
     * Envia alerta de exceção não tratada no sistema.
     */
    @Async
    public void alertarExcecao(com.delibera.model.entity.ErroLog erro) {
        String prefixo = (ambiente != null && !ambiente.isBlank() && !"PROD".equalsIgnoreCase(ambiente))
                ? "[" + ambiente.toUpperCase() + "] " : "";

        String assuntoAlerta = prefixo + "🔴 EXCEÇÃO — " + erro.getTipo().substring(erro.getTipo().lastIndexOf('.') + 1);

        // Limitar stacktrace no email
        String stackResumido = erro.getStacktrace();
        if (stackResumido != null && stackResumido.length() > 2000) {
            stackResumido = stackResumido.substring(0, 2000) + "\n... (ver completo no painel)";
        }

        String corpo = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:0;background:#f8fafc;font-family:Arial,sans-serif;'>"
                + "<table width='100%' cellpadding='0' cellspacing='0' style='padding:2rem;'>"
                + "<tr><td align='center'>"
                + "<table width='600' cellpadding='0' cellspacing='0' style='background:#fff;border-radius:10px;border:2px solid #ef4444;'>"
                + "<tr><td style='background:#ef4444;padding:1rem 1.5rem;color:white;font-weight:bold;font-size:1rem;border-radius:8px 8px 0 0;'>"
                + "🔴 Exceção não tratada</td></tr>"
                + "<tr><td style='padding:1.5rem;'>"
                + "<table style='width:100%;font-size:0.85rem;color:#334155;border-collapse:collapse;'>"
                + "<tr><td style='padding:0.5rem 0;font-weight:600;color:#64748b;width:100px;vertical-align:top;'>ID</td>"
                + "<td style='padding:0.5rem 0;'>#" + erro.getId() + "</td></tr>"
                + "<tr><td style='padding:0.5rem 0;font-weight:600;color:#64748b;vertical-align:top;'>Tipo</td>"
                + "<td style='padding:0.5rem 0;color:#ef4444;font-weight:600;'>" + erro.getTipo() + "</td></tr>"
                + "<tr><td style='padding:0.5rem 0;font-weight:600;color:#64748b;vertical-align:top;'>Mensagem</td>"
                + "<td style='padding:0.5rem 0;'>" + (erro.getMensagem() != null ? erro.getMensagem() : "null") + "</td></tr>"
                + "<tr><td style='padding:0.5rem 0;font-weight:600;color:#64748b;vertical-align:top;'>URL</td>"
                + "<td style='padding:0.5rem 0;'>" + erro.getMetodoHttp() + " " + erro.getUrl() + "</td></tr>"
                + "<tr><td style='padding:0.5rem 0;font-weight:600;color:#64748b;vertical-align:top;'>Usuário</td>"
                + "<td style='padding:0.5rem 0;'>" + (erro.getUsuario() != null ? erro.getUsuario() : "anônimo") + "</td></tr>"
                + "<tr><td style='padding:0.5rem 0;font-weight:600;color:#64748b;vertical-align:top;'>IP</td>"
                + "<td style='padding:0.5rem 0;'>" + erro.getIp() + "</td></tr>"
                + "<tr><td style='padding:0.5rem 0;font-weight:600;color:#64748b;vertical-align:top;'>Data</td>"
                + "<td style='padding:0.5rem 0;'>" + erro.getCriadoEm().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "</td></tr>"
                + "</table>"
                + "<div style='margin-top:1rem;background:#fef2f2;border-radius:6px;padding:0.75rem 1rem;'>"
                + "<div style='font-size:0.72rem;font-weight:700;color:#991b1b;text-transform:uppercase;margin-bottom:0.5rem;'>Stacktrace</div>"
                + "<pre style='margin:0;font-size:0.7rem;color:#7f1d1d;white-space:pre-wrap;word-wrap:break-word;max-height:400px;overflow:auto;'>"
                + (stackResumido != null ? stackResumido.replace("<", "&lt;").replace(">", "&gt;") : "N/A")
                + "</pre></div>"
                + "</td></tr></table>"
                + "</td></tr></table></body></html>";

        if (!emailHabilitado) {
            logger.warn("[ALERTA EXCECAO SIMULADO] ErroLog={} | Tipo: {} | URL: {}",
                    erro.getId(), erro.getTipo(), erro.getUrl());
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(remetente);
            helper.setSubject(assuntoAlerta);
            helper.setText(corpo, true);
            mailSender.send(msg);
            logger.info("[ALERTA EXCECAO ENVIADO] ErroLog={}", erro.getId());
        } catch (Exception e) {
            logger.error("[ALERTA EXCECAO FALHOU] ErroLog={} | Erro: {}", erro.getId(), e.getMessage());
        }
    }
}
