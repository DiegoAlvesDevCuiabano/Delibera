package com.delibera.integration;

import com.delibera.model.entity.EmailLog;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.EmailLogRepository;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.SolicitacaoRepository;
import com.delibera.service.EmailHtmlService;
import com.delibera.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Teste de integração do fluxo completo de email.
 * Usa @SpringBootTest com H2 in-memory e JavaMailSender mockado
 * para validar que emails são enviados e registrados no EmailLog.
 */
@SpringBootTest
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "delibera.email.enabled=true",
        "delibera.email.from=teste@delibera.com",
        "delibera.ambiente=TEST",
        "delibera.base-url=http://localhost:8080",
        "spring.task.execution.pool.core-size=1",
        "management.health.mail.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailIntegrationTest {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailHtmlService emailHtmlService;

    @Autowired
    private EmailLogRepository emailLogRepository;

    @Autowired
    private SolicitacaoRepository solicitacaoRepository;

    @Autowired
    private InstituicaoRepository instituicaoRepository;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        emailLogRepository.deleteAll();
        mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private Instituicao criarInstituicao() {
        Instituicao inst = new Instituicao("Faculdade Integração", "FI");
        inst.setDominio("integracao.edu.br");
        return instituicaoRepository.save(inst);
    }

    private Solicitacao criarSolicitacao(Instituicao inst) {
        Solicitacao s = new Solicitacao();
        s.setTipo(TipoSolicitacao.ABONO_FALTA);
        s.setStatus(StatusSolicitacao.EM_ANALISE);
        s.setProtocolo("DEL-2026-" + System.nanoTime() % 100000000);
        s.setAlunoNome("Teste Integração");
        s.setAlunoEmail("teste@integracao.com");
        s.setDisciplina("Testes");
        s.setJustificativa("Teste de integração");
        s.setInstituicao(inst);
        s.setDataAceiteTermos(LocalDateTime.now());
        s.setVersaoTermosAceita("1.0");
        return solicitacaoRepository.save(s);
    }

    private void aguardarAsync() throws InterruptedException {
        Thread.sleep(1000);
    }

    // ── EmailService ─────────────────────────────────────────────

    @Test
    @DisplayName("notificarCriacao — fluxo completo: envia email e persiste EmailLog ENVIADO")
    void notificarCriacao_fullFlow() throws Exception {
        // Arrange
        Instituicao inst = criarInstituicao();
        Solicitacao s = criarSolicitacao(inst);

        // Act
        emailService.notificarCriacao(s);
        aguardarAsync();

        // Assert
        verify(mailSender, atLeastOnce()).createMimeMessage();
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        List<EmailLog> logs = emailLogRepository.findBySolicitacaoIdOrderByEnviadoEmDesc(s.getId());
        assertThat(logs).isNotEmpty();

        EmailLog log = logs.get(0);
        assertThat(log.getStatus()).isEqualTo("ENVIADO");
        assertThat(log.getDestinatario()).isEqualTo("teste@integracao.com");
        assertThat(log.getAssunto()).contains(s.getProtocolo());
        assertThat(log.getSolicitacao().getId()).isEqualTo(s.getId());
        assertThat(log.getErro()).isNull();
    }

    @Test
    @DisplayName("notificarCriacao — falha SMTP salva EmailLog com status FALHOU e erro preenchido")
    void notificarCriacao_falha_salvaLogEAlerta() throws Exception {
        // Arrange
        Instituicao inst = criarInstituicao();
        Solicitacao s = criarSolicitacao(inst);
        doThrow(new MessagingException("SMTP error")).when(mimeMessage).setRecipient(any(), any());

        // Act
        emailService.notificarCriacao(s);
        aguardarAsync();

        // Assert
        List<EmailLog> logs = emailLogRepository.findBySolicitacaoIdOrderByEnviadoEmDesc(s.getId());
        assertThat(logs).isNotEmpty();

        EmailLog log = logs.get(0);
        assertThat(log.getStatus()).isEqualTo("FALHOU");
        assertThat(log.getErro()).isNotNull();
        assertThat(log.getErro()).contains("SMTP error");

        // Reset mock for other tests
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("notificarMudancaStatus — fluxo completo: persiste EmailLog com status atualizado")
    void notificarMudancaStatus_fullFlow() throws Exception {
        // Arrange
        Instituicao inst = criarInstituicao();
        Solicitacao s = criarSolicitacao(inst);
        s.setStatus(StatusSolicitacao.RESOLVIDO);
        s.setParecer("Deferido pela coordenação.");
        s = solicitacaoRepository.save(s);

        // Act
        emailService.notificarMudancaStatus(s);
        aguardarAsync();

        // Assert
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        List<EmailLog> logs = emailLogRepository.findBySolicitacaoIdOrderByEnviadoEmDesc(s.getId());
        assertThat(logs).isNotEmpty();

        EmailLog log = logs.get(0);
        assertThat(log.getStatus()).isEqualTo("ENVIADO");
        assertThat(log.getDestinatario()).isEqualTo("teste@integracao.com");
        assertThat(log.getAssunto()).contains("Resolvido");
        assertThat(log.getSolicitacao().getId()).isEqualTo(s.getId());
    }

    // ── EmailHtmlService ─────────────────────────────────────────

    @Test
    @DisplayName("enviarSolicitacaoCriadaHtml — fluxo completo: persiste EmailLog com prefixo [HTML]")
    void enviarSolicitacaoCriadaHtml_fullFlow() throws Exception {
        // Arrange
        Instituicao inst = criarInstituicao();
        Solicitacao s = criarSolicitacao(inst);

        // Act
        emailHtmlService.enviarSolicitacaoCriadaHtml(s);
        aguardarAsync();

        // Assert
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        List<EmailLog> logs = emailLogRepository.findBySolicitacaoIdOrderByEnviadoEmDesc(s.getId());
        assertThat(logs).isNotEmpty();

        EmailLog log = logs.get(0);
        assertThat(log.getStatus()).isEqualTo("ENVIADO");
        assertThat(log.getDestinatario()).isEqualTo("teste@integracao.com");
        assertThat(log.getCorpo()).startsWith("[HTML]");
        assertThat(log.getAssunto()).contains(s.getProtocolo());
    }

    @Test
    @DisplayName("enviarContatoSuporte — fluxo completo: persiste EmailLog para mensagem de suporte")
    void enviarContatoSuporte_fullFlow() throws Exception {
        // Act
        emailHtmlService.enviarContatoSuporte(
                "João Teste",
                "joao@teste.com",
                "Dúvida sobre o sistema",
                "Gostaria de saber como funciona o fluxo de solicitações."
        );
        aguardarAsync();

        // Assert
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        List<EmailLog> allLogs = emailLogRepository.findAll();
        EmailLog log = allLogs.stream()
                .filter(l -> l.getAssunto().contains("Suporte"))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getStatus()).isEqualTo("ENVIADO");
        assertThat(log.getDestinatario()).isEqualTo("teste@delibera.com");
        assertThat(log.getCorpo()).startsWith("[HTML]");
        assertThat(log.getSolicitacao()).isNull();
    }

    @Test
    @DisplayName("enviarVoucherExtensao — fluxo completo: persiste EmailLog com domínio da instituição")
    void enviarVoucherExtensao_fullFlow() throws Exception {
        // Arrange
        Instituicao inst = criarInstituicao();

        // Act — enviarVoucherExtensao is NOT @Async, runs synchronously
        emailHtmlService.enviarVoucherExtensao(inst);

        // Assert (no need to wait — synchronous method)
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        List<EmailLog> allLogs = emailLogRepository.findAll();
        EmailLog log = allLogs.stream()
                .filter(l -> l.getAssunto().contains("presente"))
                .findFirst()
                .orElse(null);

        assertThat(log).isNotNull();
        assertThat(log.getStatus()).isEqualTo("ENVIADO");
        assertThat(log.getDestinatario()).isEqualTo("contato@integracao.edu.br");
        assertThat(log.getCorpo()).startsWith("[HTML]");
        assertThat(log.getSolicitacao()).isNull();
    }
}
