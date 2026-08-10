package com.delibera.service;

import com.delibera.model.entity.EmailLog;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailHtmlServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private AlertaErroService alertaErroService;

    @Mock
    private MimeMessage mimeMessage;

    private EmailHtmlService serviceDesabilitado;
    private EmailHtmlService serviceHabilitado;

    private Instituicao instituicao;
    private Solicitacao solicitacao;

    @BeforeEach
    void setUp() {
        // Service com email desabilitado
        serviceDesabilitado = new EmailHtmlService(mailSender, emailLogRepository, alertaErroService, new com.delibera.util.TokenAssinado("segredo-de-teste", "LOCAL"), false);
        ReflectionTestUtils.setField(serviceDesabilitado, "remetente", "contato@appdelibera.com.br");
        ReflectionTestUtils.setField(serviceDesabilitado, "baseUrl", "https://appdelibera.com.br");
        ReflectionTestUtils.setField(serviceDesabilitado, "ambiente", "");

        // Service com email habilitado
        serviceHabilitado = new EmailHtmlService(mailSender, emailLogRepository, alertaErroService, new com.delibera.util.TokenAssinado("segredo-de-teste", "LOCAL"), true);
        ReflectionTestUtils.setField(serviceHabilitado, "remetente", "contato@appdelibera.com.br");
        ReflectionTestUtils.setField(serviceHabilitado, "baseUrl", "https://appdelibera.com.br");
        ReflectionTestUtils.setField(serviceHabilitado, "ambiente", "");

        // Instituicao de teste
        instituicao = new Instituicao("Faculdade Teste", "FT");
        instituicao.setId(1L);
        instituicao.setDominio("faculdadeteste.edu.br");

        // Solicitacao de teste
        solicitacao = new Solicitacao();
        solicitacao.setProtocolo("DEL-20260503-ABC1");
        solicitacao.setAlunoNome("Maria Silva");
        solicitacao.setAlunoEmail("maria@email.com");
        solicitacao.setTipo(TipoSolicitacao.SEGUNDA_CHAMADA);
        solicitacao.setDisciplina("Cálculo I");
        solicitacao.setCriadoEm(LocalDateTime.now());
    }

    // ── enviarVoucherExtensao ──────────────────────────────────

    @Test
    @DisplayName("enviarVoucherExtensao — email desabilitado salva log SIMULADO")
    void enviarVoucherExtensao_desabilitado_salvaSimulado() {
        serviceDesabilitado.enviarVoucherExtensao(instituicao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertEquals("contato@faculdadeteste.edu.br", log.getDestinatario());
        assertTrue(log.getAssunto().contains("30 dias foi pouco"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("enviarVoucherExtensao — email habilitado envia e salva ENVIADO")
    void enviarVoucherExtensao_habilitado_enviaESalvaEnviado() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        serviceHabilitado.enviarVoucherExtensao(instituicao);

        verify(mailSender).send(mimeMessage);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertEquals("ENVIADO", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("enviarVoucherExtensao — falha no envio salva FALHOU e chama alertaErro")
    void enviarVoucherExtensao_falha_salvaFalhouEAlerta() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new org.springframework.mail.MailSendException("SMTP error"))
                .when(mailSender).send(any(MimeMessage.class));

        // MimeMessageHelper.setFrom/setTo/setSubject/setText throw MessagingException
        // but the actual send is what we mock to fail. We need the helper calls to work.
        // Since MimeMessage is a mock, helper methods on it won't actually fail.
        // The service catches MessagingException — MailSendException is not MessagingException.
        // Let's use a different approach: make createMimeMessage work but throw on send via helper.

        // Actually, looking at the code, the catch is on MessagingException.
        // Let's mock MimeMessage properly so helper.setFrom works, then make mailSender.send throw.
        // MailSendException extends MailException, not MessagingException.
        // The service only catches MessagingException, so MailSendException won't be caught.
        // We need to simulate a MessagingException from helper methods instead.

        // Reset and make MimeMessage.setRecipient throw MessagingException
        reset(mailSender);
        MimeMessage failMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(failMessage);
        doThrow(new MessagingException("SMTP connection refused"))
                .when(failMessage).setRecipient(any(), any());

        serviceHabilitado.enviarVoucherExtensao(instituicao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("FALHOU", log.getStatus());
        assertNotNull(log.getErro());
        verify(alertaErroService).alertarFalhaEmail(any(), anyString(), anyString(), anyString());
    }

    // ── enviarConviteRetorno ───────────────────────────────────

    @Test
    @DisplayName("enviarConviteRetorno — email desabilitado salva SIMULADO com assunto correto")
    void enviarConviteRetorno_desabilitado_salvaSimulado() {
        serviceDesabilitado.enviarConviteRetorno(instituicao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertTrue(log.getAssunto().contains("Sua conta está pronta"));
    }

    // ── enviarContatoFinal ────────────────────────────────────

    @Test
    @DisplayName("enviarContatoFinal — email desabilitado salva SIMULADO com assunto correto")
    void enviarContatoFinal_desabilitado_salvaSimulado() {
        serviceDesabilitado.enviarContatoFinal(instituicao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertTrue(log.getAssunto().contains("período de avaliação encerrou"));
    }

    // ── enviarContatoSuporte ──────────────────────────────────

    @Test
    @DisplayName("enviarContatoSuporte — email desabilitado salva SIMULADO")
    void enviarContatoSuporte_desabilitado_salvaSimulado() {
        serviceDesabilitado.enviarContatoSuporte("João", "joao@email.com", "Dúvida", "Como funciona?");

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertTrue(log.getAssunto().contains("Suporte"));
        assertEquals("contato@appdelibera.com.br", log.getDestinatario());
    }

    // ── enviarSolicitacaoCriadaHtml ───────────────────────────

    @Test
    @DisplayName("enviarSolicitacaoCriadaHtml — email desabilitado, corpo contém protocolo e nome")
    void enviarSolicitacaoCriadaHtml_desabilitado_conteudoCorreto() {
        serviceDesabilitado.enviarSolicitacaoCriadaHtml(solicitacao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        EmailLog log = captor.getValue();
        assertEquals("SIMULADO", log.getStatus());
        assertTrue(log.getAssunto().contains("DEL-20260503-ABC1"));
        assertEquals("maria@email.com", log.getDestinatario());
        assertSame(solicitacao, log.getSolicitacao());
    }

    @Test
    @DisplayName("enviarSolicitacaoCriadaHtml — email habilitado envia email")
    void enviarSolicitacaoCriadaHtml_habilitado_enviaEmail() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        serviceHabilitado.enviarSolicitacaoCriadaHtml(solicitacao);

        verify(mailSender).send(mimeMessage);
        verify(emailLogRepository).save(any(EmailLog.class));
    }

    // ── Prefixo de ambiente ───────────────────────────────────

    @Test
    @DisplayName("Ambiente DEV adiciona prefixo [DEV] no assunto")
    void ambienteDev_adicionaPrefixo() {
        ReflectionTestUtils.setField(serviceDesabilitado, "ambiente", "DEV");

        serviceDesabilitado.enviarVoucherExtensao(instituicao);

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());

        assertTrue(captor.getValue().getAssunto().startsWith("[DEV] "));
    }
}
