package com.delibera.service;

import com.delibera.model.entity.Solicitacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean emailHabilitado;

    @Value("${delibera.email.from:noreply@delibera.com.br}")
    private String remetente;

    public EmailService(JavaMailSender mailSender,
                        @Value("${delibera.email.enabled:false}") boolean emailHabilitado) {
        this.mailSender = mailSender;
        this.emailHabilitado = emailHabilitado;
    }

    @Async
    public void notificarCriacao(Solicitacao s) {
        String assunto = "Delibera — Solicitação registrada: " + s.getProtocolo();
        String corpo = String.format("""
                Olá, %s!

                Sua solicitação foi registrada com sucesso.

                Protocolo: %s
                Tipo: %s
                Disciplina: %s

                Você pode acompanhar o status a qualquer momento usando o protocolo acima.

                Resposta esperada em até 3 dias úteis.

                — Delibera
                """,
                s.getAlunoNome().split(" ")[0],
                s.getProtocolo(),
                s.getTipo().getDescricao(),
                s.getDisciplina()
        );
        enviar(s.getAlunoEmail(), assunto, corpo);
    }

    @Async
    public void notificarMudancaStatus(Solicitacao s) {
        String assunto = "Delibera — Atualização: " + s.getProtocolo() + " → " + s.getStatus().getDescricao();
        String corpo = String.format("""
                Olá, %s!

                Sua solicitação %s teve uma atualização:

                Novo status: %s
                %s

                Acesse o sistema para mais detalhes.

                — Delibera
                """,
                s.getAlunoNome().split(" ")[0],
                s.getProtocolo(),
                s.getStatus().getDescricao(),
                s.getParecer() != null ? "Parecer: " + s.getParecer() : ""
        );
        enviar(s.getAlunoEmail(), assunto, corpo);
    }

    private void enviar(String para, String assunto, String corpo) {
        if (!emailHabilitado) {
            logger.info("[EMAIL SIMULADO] Para: {} | Assunto: {}", para, assunto);
            logger.debug("[EMAIL CORPO]\n{}", corpo);
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(corpo);
            mailSender.send(msg);
            logger.info("[EMAIL ENVIADO] Para: {} | Assunto: {}", para, assunto);
        } catch (MessagingException e) {
            logger.error("[EMAIL ERRO] Para: {} | Erro: {}", para, e.getMessage());
        }
    }
}
