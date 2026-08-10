package com.delibera.service;

import com.delibera.model.entity.EmailLog;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private AlertaErroService alertaErroService;

    private EmailService serviceDesabilitado;
    private EmailService serviceHabilitado;

    private Solicitacao solicitacao;

    @BeforeEach
    void setUp() {
        serviceDesabilitado = new EmailService(mailSender, emailLogRepository, alertaErroService, false);
        serviceHabilitado = new EmailService(mailSender, emailLogRepository, alertaErroService, true);
        ReflectionTestUtils.setField(serviceHabilitado, "remetente", "contato@appdelibera.com.br");

        Instituicao inst = new Instituicao("Faculdade Teste", "FT");
        inst.setId(1L);

        solicitacao = new Solicitacao();
        solicitacao.setId(1L);
        solicitacao.setProtocolo("DEL-20260421-ABC1");
        solicitacao.setTipo(TipoSolicitacao.SEGUNDA_CHAMADA);
        solicitacao.setStatus(StatusSolicitacao.EM_ANALISE);
        solicitacao.setAlunoNome("Maria Silva");
        solicitacao.setAlunoEmail("maria@email.com");
        solicitacao.setAlunoWhatsapp("65999998888");
        solicitacao.setAlunoCurso("Engenharia de Software");
        solicitacao.setAlunoTurma("T01");
        solicitacao.setDisciplina("Banco de Dados II");
        solicitacao.setProfessor("Prof. Carlos");
        solicitacao.setDataOcorrencia(LocalDate.of(2026, 4, 15));
        solicitacao.setJustificativa("Estava com atestado médico no dia da prova.");
        solicitacao.setInstituicao(inst);
        solicitacao.setCriadoEm(LocalDateTime.of(2026, 4, 21, 10, 30));
    }

    // ── notificarCriacao — email desabilitado (SIMULADO) ──

    @Test
    void notificarCriacao_emailDesabilitado_salvaLogComStatusSimulado() {
        serviceDesabilitado.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertEquals("maria@email.com", log.getDestinatario());
        assertSame(solicitacao, log.getSolicitacao());
        assertNull(log.getErro());
    }

    @Test
    void notificarCriacao_emailDesabilitado_naoEnviaEmail() {
        serviceDesabilitado.notificarCriacao(solicitacao);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void notificarCriacao_corpoContemDadosDoAluno() {
        serviceDesabilitado.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String corpo = captor.getValue().getCorpo();

        assertAll("corpo deve conter todos os dados do aluno",
                () -> assertTrue(corpo.contains("DEL-20260421-ABC1"), "protocolo"),
                () -> assertTrue(corpo.contains("Maria Silva"), "nome completo"),
                () -> assertTrue(corpo.contains("maria@email.com"), "email"),
                () -> assertTrue(corpo.contains("65999998888"), "whatsapp"),
                () -> assertTrue(corpo.contains("Faculdade Teste"), "instituicao"),
                () -> assertTrue(corpo.contains("Segunda Chamada"), "tipo descricao"),
                () -> assertTrue(corpo.contains("Banco de Dados II"), "disciplina"),
                () -> assertTrue(corpo.contains("Engenharia de Software"), "curso"),
                () -> assertTrue(corpo.contains("T01"), "turma"),
                () -> assertTrue(corpo.contains("15/04/2026"), "data ocorrencia"),
                () -> assertTrue(corpo.contains("Estava com atestado médico"), "justificativa"),
                () -> assertTrue(corpo.contains("21/04/2026"), "data registro")
        );
    }

    @Test
    void notificarCriacao_assuntoContemProtocolo() {
        serviceDesabilitado.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertTrue(captor.getValue().getAssunto().contains("DEL-20260421-ABC1"));
    }

    @Test
    void notificarCriacao_saudacaoUsaPrimeiroNome() {
        serviceDesabilitado.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertTrue(captor.getValue().getCorpo().contains("Olá, Maria!"));
    }

    // ── notificarMudancaStatus ──

    @Test
    void notificarMudancaStatus_salvaLogComStatusSimulado() {
        solicitacao.setStatus(StatusSolicitacao.RESOLVIDO);
        solicitacao.setParecer("Deferido conforme atestado apresentado.");

        serviceDesabilitado.notificarMudancaStatus(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertSame(solicitacao, log.getSolicitacao());
    }

    @Test
    void notificarMudancaStatus_corpoContemStatusEParecer() {
        solicitacao.setStatus(StatusSolicitacao.RESOLVIDO);
        solicitacao.setParecer("Deferido conforme atestado apresentado.");

        serviceDesabilitado.notificarMudancaStatus(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String corpo = captor.getValue().getCorpo();

        assertAll("corpo deve conter status e parecer",
                () -> assertTrue(corpo.contains("Resolvido"), "status descricao"),
                () -> assertTrue(corpo.contains("Deferido conforme atestado apresentado."), "parecer"),
                () -> assertTrue(corpo.contains("Parecer da coordenação"), "label parecer")
        );
    }

    @Test
    void notificarMudancaStatus_semParecer_corpoNaoContemSecaoParecer() {
        solicitacao.setStatus(StatusSolicitacao.NEGADO);
        solicitacao.setParecer(null);

        serviceDesabilitado.notificarMudancaStatus(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String corpo = captor.getValue().getCorpo();

        assertFalse(corpo.contains("Parecer da coordenação"), "nao deve conter secao de parecer");
    }

    @Test
    void notificarMudancaStatus_assuntoContemProtocoloEStatus() {
        solicitacao.setStatus(StatusSolicitacao.NEGADO);

        serviceDesabilitado.notificarMudancaStatus(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String assunto = captor.getValue().getAssunto();

        assertAll("assunto deve conter protocolo e status",
                () -> assertTrue(assunto.contains("DEL-20260421-ABC1"), "protocolo no assunto"),
                () -> assertTrue(assunto.contains("Negado"), "status no assunto")
        );
    }

    // ── Envio real (email habilitado) ──

    @Test
    void notificarCriacao_emailHabilitado_enviaESalvaLogEnviado() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        serviceHabilitado.notificarCriacao(solicitacao);

        verify(mailSender).send(mimeMessage);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertEquals("ENVIADO", captor.getValue().getStatus());
    }

    // ── Falha no envio ──

    @Test
    void notificarCriacao_falhaNoEnvio_salvaLogComStatusFalhou() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        // MimeMessageHelper.setTo() calls mimeMessage.setRecipient() which throws MessagingException
        doThrow(new MessagingException("SMTP connection refused"))
                .when(mimeMessage).setRecipient(any(), any());

        serviceHabilitado.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("FALHOU", log.getStatus());
        assertNotNull(log.getErro());
        assertSame(solicitacao, log.getSolicitacao());
    }

    @Test
    void notificarMudancaStatus_falhaNoEnvio_salvaLogComStatusFalhou() throws Exception {
        solicitacao.setStatus(StatusSolicitacao.RESOLVIDO);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MessagingException("Timeout"))
                .when(mimeMessage).setRecipient(any(), any());

        serviceHabilitado.notificarMudancaStatus(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("FALHOU", log.getStatus());
        assertNotNull(log.getErro());
    }

    // ── Campos opcionais omitidos ──

    @Test
    void notificarCriacao_semWhatsapp_corpoNaoContemWhatsApp() {
        solicitacao.setAlunoWhatsapp(null);

        serviceDesabilitado.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertFalse(captor.getValue().getCorpo().contains("WhatsApp:"));
    }

    @Test
    void notificarCriacao_semCurso_corpoNaoContemCurso() {
        solicitacao.setAlunoCurso(null);

        serviceDesabilitado.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertFalse(captor.getValue().getCorpo().contains("Curso:"));
    }

    // ── notificarProfessor ──

    @Test
    void notificarProfessor_emailDesabilitado_salvaLogSimuladoParaProfessor() {
        serviceDesabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", "Verificar atestado.");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertEquals("prof@email.com", log.getDestinatario());
        assertSame(solicitacao, log.getSolicitacao());
    }

    @Test
    void notificarProfessor_corpoContemProtocoloCodigoDisciplinaTipoJustificativa() {
        serviceDesabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", null);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String corpo = captor.getValue().getCorpo();

        assertAll("corpo deve conter dados essenciais",
                () -> assertTrue(corpo.contains("DEL-20260421-ABC1"), "protocolo"),
                () -> assertTrue(corpo.contains("XYZ123"), "codigo de acesso"),
                () -> assertTrue(corpo.contains("Banco de Dados II"), "disciplina"),
                () -> assertTrue(corpo.contains("Segunda Chamada"), "tipo descricao"),
                () -> assertTrue(corpo.contains("Estava com atestado médico"), "justificativa")
        );
    }

    @Test
    void notificarProfessor_comComentarioCoordenador_corpoContemComentario() {
        serviceDesabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", "Verificar atestado anexado.");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String corpo = captor.getValue().getCorpo();

        assertTrue(corpo.contains("Verificar atestado anexado."), "comentario do coordenador");
        assertTrue(corpo.contains("coordenação"), "label observacao coordenacao");
    }

    @Test
    void notificarProfessor_semComentarioCoordenador_corpoNaoContemObservacao() {
        serviceDesabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", null);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String corpo = captor.getValue().getCorpo();

        assertFalse(corpo.contains("Observação da coordenação"), "nao deve conter secao de observacao");
    }

    @Test
    void notificarProfessor_comentarioBlank_corpoNaoContemObservacao() {
        serviceDesabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", "   ");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        String corpo = captor.getValue().getCorpo();

        assertFalse(corpo.contains("Observação da coordenação"), "nao deve conter secao de observacao para blank");
    }

    @Test
    void notificarProfessor_assuntoContemProtocolo() {
        serviceDesabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", null);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertTrue(captor.getValue().getAssunto().contains("DEL-20260421-ABC1"));
    }

    @Test
    void notificarProfessor_comDataOcorrencia_corpoContemData() {
        serviceDesabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", null);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertTrue(captor.getValue().getCorpo().contains("15/04/2026"), "data ocorrencia formatada");
    }

    @Test
    void notificarProfessor_emailHabilitado_enviaESalvaLogEnviado() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        serviceHabilitado.notificarProfessor(solicitacao, "prof@email.com", "XYZ123", "Verificar.");

        verify(mailSender).send(mimeMessage);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertEquals("ENVIADO", captor.getValue().getStatus());
    }

    // ── Prefixo de ambiente ──

    @Test
    void notificarCriacao_ambienteDev_assuntoContemPrefixoDev() {
        EmailService serviceDev = new EmailService(mailSender, emailLogRepository, alertaErroService, false);
        ReflectionTestUtils.setField(serviceDev, "ambiente", "DEV");

        serviceDev.notificarCriacao(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertTrue(captor.getValue().getAssunto().startsWith("[DEV] "), "assunto deve iniciar com [DEV]");
    }
}
