package com.delibera.service;

import com.delibera.model.entity.ErroLog;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertaErroServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private AlertaErroService serviceDesabilitado;
    private AlertaErroService serviceHabilitado;

    @BeforeEach
    void setUp() {
        serviceDesabilitado = new AlertaErroService(mailSender, false);
        ReflectionTestUtils.setField(serviceDesabilitado, "remetente", "contato@appdelibera.com.br");
        ReflectionTestUtils.setField(serviceDesabilitado, "ambiente", "");

        serviceHabilitado = new AlertaErroService(mailSender, true);
        ReflectionTestUtils.setField(serviceHabilitado, "remetente", "contato@appdelibera.com.br");
        ReflectionTestUtils.setField(serviceHabilitado, "ambiente", "");
    }

    private ErroLog criarErroLog() {
        ErroLog erro = new ErroLog();
        erro.setId(42L);
        erro.setTipo("java.lang.NullPointerException");
        erro.setMensagem("Cannot invoke method on null");
        erro.setStacktrace("java.lang.NullPointerException: Cannot invoke method on null\n\tat com.delibera.Foo.bar(Foo.java:10)");
        erro.setUrl("/api/solicitacoes");
        erro.setMetodoHttp("POST");
        erro.setUsuario("admin@test.com");
        erro.setIp("192.168.1.1");
        erro.setCriadoEm(LocalDateTime.of(2026, 5, 1, 14, 30, 0));
        return erro;
    }

    // ── alertarFalhaEmail ──

    @Test
    void alertarFalhaEmail_desabilitado_naoEnviaEmail() {
        serviceDesabilitado.alertarFalhaEmail(1L, "aluno@test.com", "Assunto teste", "SMTP error");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void alertarFalhaEmail_habilitado_enviaEmailParaSiMesmo() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        serviceHabilitado.alertarFalhaEmail(1L, "aluno@test.com", "Assunto teste", "SMTP error");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void alertarFalhaEmail_falhaNoEnvio_naoLancaExcecao() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MessagingException("Connection refused"))
                .when(mimeMessage).setRecipient(any(), any());

        assertDoesNotThrow(() ->
                serviceHabilitado.alertarFalhaEmail(1L, "aluno@test.com", "Assunto", "Erro original"));
    }

    // ── alertarExcecao ──

    @Test
    void alertarExcecao_desabilitado_naoEnviaEmail() {
        ErroLog erro = criarErroLog();

        serviceDesabilitado.alertarExcecao(erro);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void alertarExcecao_habilitado_enviaEmail() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ErroLog erro = criarErroLog();
        serviceHabilitado.alertarExcecao(erro);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void alertarExcecao_assuntoContemNomeCurtoDoTipo() {
        // The method builds: prefixo + "🔴 EXCEÇÃO — " + erro.getTipo().substring(erro.getTipo().lastIndexOf('.') + 1)
        ErroLog erro = criarErroLog();
        String tipo = erro.getTipo();
        String nomeCurto = tipo.substring(tipo.lastIndexOf('.') + 1);

        assertEquals("NullPointerException", nomeCurto);
    }

    @Test
    void alertarExcecao_stacktraceLongo_truncadoEm2000Chars() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ErroLog erro = criarErroLog();
        // Create a stacktrace longer than 2000 chars
        StringBuilder longStack = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longStack.append("at com.delibera.service.SomeService.method(SomeService.java:").append(i).append(")\n");
        }
        erro.setStacktrace(longStack.toString());
        assertTrue(erro.getStacktrace().length() > 2000, "stacktrace deve ter mais de 2000 chars");

        // Should not throw — internally truncates to 2000 chars
        assertDoesNotThrow(() -> serviceHabilitado.alertarExcecao(erro));
        verify(mailSender).send(mimeMessage);
    }
}
