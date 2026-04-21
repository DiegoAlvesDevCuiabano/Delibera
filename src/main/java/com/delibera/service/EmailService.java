package com.delibera.service;

import com.delibera.model.entity.EmailLog;
import com.delibera.model.entity.Solicitacao;
import com.delibera.repository.EmailLogRepository;
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

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final boolean emailHabilitado;

    @Value("${delibera.email.from:contato@appdelibera.com.br}")
    private String remetente;

    public EmailService(JavaMailSender mailSender,
                        EmailLogRepository emailLogRepository,
                        @Value("${delibera.email.enabled:false}") boolean emailHabilitado) {
        this.mailSender = mailSender;
        this.emailLogRepository = emailLogRepository;
        this.emailHabilitado = emailHabilitado;
    }

    @Async
    public void notificarCriacao(Solicitacao s) {
        String assunto = "Delibera — Solicitação registrada: " + s.getProtocolo();

        StringBuilder sb = new StringBuilder();
        sb.append("Olá, ").append(s.getAlunoNome().split(" ")[0]).append("!\n\n");
        sb.append("Sua solicitação foi registrada com sucesso.\n\n");

        sb.append("════════════════════════════════════\n");
        sb.append("  PROTOCOLO: ").append(s.getProtocolo()).append("\n");
        sb.append("════════════════════════════════════\n\n");

        sb.append("Guarde este número — ele é sua chave de acompanhamento.\n\n");

        sb.append("── Dados informados ──────────────\n\n");
        sb.append("Nome: ").append(s.getAlunoNome()).append("\n");
        sb.append("Email: ").append(s.getAlunoEmail()).append("\n");
        if (s.getAlunoWhatsapp() != null && !s.getAlunoWhatsapp().isBlank()) {
            sb.append("WhatsApp: ").append(s.getAlunoWhatsapp()).append("\n");
        }
        sb.append("Instituição: ").append(s.getInstituicao().getNome()).append("\n");
        if (s.getAlunoCurso() != null && !s.getAlunoCurso().isBlank()) {
            sb.append("Curso: ").append(s.getAlunoCurso());
            if (s.getAlunoTurma() != null && !s.getAlunoTurma().isBlank()) {
                sb.append(" — Turma: ").append(s.getAlunoTurma());
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("── Solicitação ───────────────────\n\n");
        sb.append("Tipo: ").append(s.getTipo().getDescricao()).append("\n");
        sb.append("Disciplina: ").append(s.getDisciplina()).append("\n");
        if (s.getDataOcorrencia() != null) {
            sb.append("Data relacionada: ").append(s.getDataOcorrencia().format(FMT_DATA)).append("\n");
        }
        if (s.getDatasFalta() != null && !s.getDatasFalta().isBlank()) {
            sb.append("Datas de falta: ").append(s.getDatasFalta()).append("\n");
        }
        if (s.getNotaAtual() != null && !s.getNotaAtual().isBlank()) {
            sb.append("Nota atual: ").append(s.getNotaAtual()).append("\n");
        }
        if (s.getNotaEsperada() != null && !s.getNotaEsperada().isBlank()) {
            sb.append("Nota esperada: ").append(s.getNotaEsperada()).append("\n");
        }
        sb.append("\nJustificativa:\n").append(s.getJustificativa()).append("\n\n");

        sb.append("── Próximos passos ───────────────\n\n");
        sb.append("• Sua solicitação será analisada pela coordenação\n");
        sb.append("• Resposta esperada em até 3 dias úteis\n");
        sb.append("• Você receberá um email a cada atualização de status\n");
        sb.append("• Consulte a qualquer momento em: consultar protocolo\n\n");

        sb.append("Data de registro: ").append(s.getCriadoEm().format(FMT_DATA_HORA)).append("\n\n");
        sb.append("— Delibera\n");

        enviar(s, s.getAlunoEmail(), assunto, sb.toString());
    }

    @Async
    public void notificarMudancaStatus(Solicitacao s) {
        String assunto = "Delibera — Atualização: " + s.getProtocolo() + " → " + s.getStatus().getDescricao();

        StringBuilder sb = new StringBuilder();
        sb.append("Olá, ").append(s.getAlunoNome().split(" ")[0]).append("!\n\n");
        sb.append("Sua solicitação teve uma atualização.\n\n");

        sb.append("════════════════════════════════════\n");
        sb.append("  PROTOCOLO: ").append(s.getProtocolo()).append("\n");
        sb.append("  STATUS: ").append(s.getStatus().getDescricao()).append("\n");
        sb.append("════════════════════════════════════\n\n");

        if (s.getParecer() != null && !s.getParecer().isBlank()) {
            sb.append("Parecer da coordenação:\n");
            sb.append(s.getParecer()).append("\n\n");
        }

        sb.append("Tipo: ").append(s.getTipo().getDescricao()).append("\n");
        sb.append("Disciplina: ").append(s.getDisciplina()).append("\n\n");

        sb.append("Você pode consultar o status completo a qualquer momento.\n\n");
        sb.append("— Delibera\n");

        enviar(s, s.getAlunoEmail(), assunto, sb.toString());
    }

    private void enviar(Solicitacao solicitacao, String para, String assunto, String corpo) {
        EmailLog log = new EmailLog();
        log.setSolicitacao(solicitacao);
        log.setDestinatario(para);
        log.setAssunto(assunto);
        log.setCorpo(corpo);

        if (!emailHabilitado) {
            log.setStatus("SIMULADO");
            emailLogRepository.save(log);
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

            log.setStatus("ENVIADO");
            emailLogRepository.save(log);
            logger.info("[EMAIL ENVIADO] Para: {} | Assunto: {}", para, assunto);
        } catch (MessagingException e) {
            log.setStatus("FALHOU");
            log.setErro(e.getMessage());
            emailLogRepository.save(log);
            logger.error("[EMAIL ERRO] Para: {} | Erro: {}", para, e.getMessage());
        }
    }
}
