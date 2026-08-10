package com.delibera.service;

import com.delibera.model.entity.ErroLog;
import com.delibera.model.entity.Instituicao;
import com.delibera.repository.ErroLogRepository;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.SolicitacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PilotoScheduledJobTest {

    @Mock
    private InstituicaoRepository instituicaoRepository;

    @Mock
    private SolicitacaoRepository solicitacaoRepository;

    @Mock
    private EmailHtmlService emailHtmlService;

    @Mock
    private ErroLogRepository erroLogRepository;

    @Mock
    private AlertaErroService alertaErroService;

    private PilotoScheduledJob job;

    @BeforeEach
    void setUp() {
        job = new PilotoScheduledJob(
                instituicaoRepository,
                solicitacaoRepository,
                emailHtmlService,
                erroLogRepository,
                alertaErroService
        );
    }

    private Instituicao criarInstituicaoPilotoExpirado(int diasAtras) {
        Instituicao inst = new Instituicao("Faculdade Teste", "FT");
        inst.setId(1L);
        inst.setDominio("teste.edu.br");
        inst.setPilotoExpiraEm(LocalDateTime.now().minusDays(diasAtras));
        inst.setEmailPilotoEnviado(false);
        return inst;
    }

    private Instituicao criarInstituicaoComVoucher(int diasAtras) {
        Instituicao inst = new Instituicao("Faculdade Voucher", "FV");
        inst.setId(2L);
        inst.setDominio("voucher.edu.br");
        inst.setVoucherAtivadoEm(LocalDateTime.now().minusDays(diasAtras));
        inst.setEmailVoucherEnviado(false);
        return inst;
    }

    // ── Piloto expirado com atividade ─────────────────────────

    @Test
    @DisplayName("Piloto expirado com atividade — envia voucher de extensao")
    void pilotoExpirado_comAtividade_enviaVoucher() {
        Instituicao inst = criarInstituicaoPilotoExpirado(3); // expirou ha 3 dias

        when(instituicaoRepository.findPilotoExpirado(any(LocalDateTime.class)))
                .thenReturn(List.of(inst));
        when(instituicaoRepository.findComVoucher())
                .thenReturn(Collections.emptyList());
        when(solicitacaoRepository.countByInstituicaoId(1L)).thenReturn(5L);

        job.verificarPilotosExpirados();

        verify(emailHtmlService).enviarVoucherExtensao(inst);
        verify(emailHtmlService, never()).enviarConviteRetorno(any());
        assertTrue(inst.isEmailPilotoEnviado());
        verify(instituicaoRepository).save(inst);
    }

    // ── Piloto expirado sem atividade ─────────────────────────

    @Test
    @DisplayName("Piloto expirado sem atividade — envia convite de retorno")
    void pilotoExpirado_semAtividade_enviaConvite() {
        Instituicao inst = criarInstituicaoPilotoExpirado(3);

        when(instituicaoRepository.findPilotoExpirado(any(LocalDateTime.class)))
                .thenReturn(List.of(inst));
        when(instituicaoRepository.findComVoucher())
                .thenReturn(Collections.emptyList());
        when(solicitacaoRepository.countByInstituicaoId(1L)).thenReturn(0L);

        job.verificarPilotosExpirados();

        verify(emailHtmlService).enviarConviteRetorno(inst);
        verify(emailHtmlService, never()).enviarVoucherExtensao(any());
        assertTrue(inst.isEmailPilotoEnviado());
        verify(instituicaoRepository).save(inst);
    }

    // ── Piloto recente (menos de 1 dia) — ignorado ────────────

    @Test
    @DisplayName("Piloto expirado ha menos de 1 dia — ignorado (grace period)")
    void pilotoExpirado_recente_ignorado() {
        // Expirou ha poucas horas (menos de 1 dia)
        Instituicao inst = new Instituicao("Faculdade Recente", "FR");
        inst.setId(3L);
        inst.setPilotoExpiraEm(LocalDateTime.now().minusHours(6));

        when(instituicaoRepository.findPilotoExpirado(any(LocalDateTime.class)))
                .thenReturn(List.of(inst));
        when(instituicaoRepository.findComVoucher())
                .thenReturn(Collections.emptyList());

        job.verificarPilotosExpirados();

        verify(emailHtmlService, never()).enviarVoucherExtensao(any());
        verify(emailHtmlService, never()).enviarConviteRetorno(any());
        verify(instituicaoRepository, never()).save(any());
    }

    // ── Voucher expirado ──────────────────────────────────────

    @Test
    @DisplayName("Voucher expirado (mais de 30 dias) — envia contato final")
    void voucherExpirado_enviaContatoFinal() {
        Instituicao inst = criarInstituicaoComVoucher(35); // voucher ativado ha 35 dias

        when(instituicaoRepository.findPilotoExpirado(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(instituicaoRepository.findComVoucher())
                .thenReturn(List.of(inst));

        job.verificarPilotosExpirados();

        verify(emailHtmlService).enviarContatoFinal(inst);
        assertTrue(inst.isEmailVoucherEnviado());
        verify(instituicaoRepository).save(inst);
    }

    // ── Voucher ainda ativo ───────────────────────────────────

    @Test
    @DisplayName("Voucher ainda ativo (menos de 30 dias) — ignorado")
    void voucherAtivo_ignorado() {
        Instituicao inst = criarInstituicaoComVoucher(10); // voucher ativado ha 10 dias

        when(instituicaoRepository.findPilotoExpirado(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(instituicaoRepository.findComVoucher())
                .thenReturn(List.of(inst));

        job.verificarPilotosExpirados();

        verify(emailHtmlService, never()).enviarContatoFinal(any());
        verify(instituicaoRepository, never()).save(any());
    }

    // ── Excecao durante execucao ──────────────────────────────

    @Test
    @DisplayName("Excecao durante job — registra ErroLog e envia alerta")
    void excecao_registraErroLog() {
        when(instituicaoRepository.findPilotoExpirado(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        when(erroLogRepository.save(any(ErroLog.class))).thenAnswer(invocation -> {
            ErroLog erro = invocation.getArgument(0);
            erro.setId(99L);
            return erro;
        });

        job.verificarPilotosExpirados();

        ArgumentCaptor<ErroLog> captor = ArgumentCaptor.forClass(ErroLog.class);
        verify(erroLogRepository, atLeastOnce()).save(captor.capture());

        ErroLog erroSalvo = captor.getAllValues().get(0);
        assertEquals("java.lang.RuntimeException", erroSalvo.getTipo());
        assertEquals("Database connection lost", erroSalvo.getMensagem());
        assertEquals("JOB", erroSalvo.getMetodoHttp());
        assertTrue(erroSalvo.getUrl().contains("PilotoScheduledJob"));
        assertNotNull(erroSalvo.getStacktrace());

        verify(alertaErroService).alertarExcecao(any(ErroLog.class));
    }

    // ── Nenhum piloto expirado ────────────────────────────────

    @Test
    @DisplayName("Nenhum piloto expirado — nao faz nada")
    void nenhumPilotoExpirado_naoFazNada() {
        when(instituicaoRepository.findPilotoExpirado(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(instituicaoRepository.findComVoucher())
                .thenReturn(Collections.emptyList());

        job.verificarPilotosExpirados();

        verify(emailHtmlService, never()).enviarVoucherExtensao(any());
        verify(emailHtmlService, never()).enviarConviteRetorno(any());
        verify(emailHtmlService, never()).enviarContatoFinal(any());
        verify(instituicaoRepository, never()).save(any());
    }
}
