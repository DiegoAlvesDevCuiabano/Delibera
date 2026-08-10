package com.delibera.service;

import com.delibera.model.entity.HistoricoSolicitacao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.repository.AnexoRepository;
import com.delibera.repository.EmailLogRepository;
import com.delibera.repository.ParecerProfessorRepository;
import com.delibera.repository.SolicitacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnonimizacaoScheduledJobTest {

    @Mock
    private SolicitacaoRepository solicitacaoRepository;
    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private ParecerProfessorRepository parecerProfessorRepository;
    @Mock
    private AnexoRepository anexoRepository;
    @Mock
    private AnexoService anexoService;

    private AnonimizacaoScheduledJob job() {
        return new AnonimizacaoScheduledJob(solicitacaoRepository, emailLogRepository,
                parecerProfessorRepository, anexoRepository, anexoService);
    }

    @Test
    void executar_deveAnonimizarDadosAcademicosEPessoais() {
        Solicitacao solicitacao = criarSolicitacao();
        when(solicitacaoRepository.findParaAnonimizar(any())).thenReturn(List.of(solicitacao));
        when(solicitacaoRepository.findAntigasParaAnonimizar(any())).thenReturn(List.of());

        job().executar();

        assertEquals("[ANONIMIZADO]", solicitacao.getAlunoNome());
        assertEquals("[ANONIMIZADO]@anonimizado.local", solicitacao.getAlunoEmail());
        assertNull(solicitacao.getAlunoWhatsapp());
        assertNull(solicitacao.getDisciplina());
        assertNull(solicitacao.getProfessor());
        assertNull(solicitacao.getDataOcorrencia());
        assertNull(solicitacao.getNotaAtual());
        assertEquals("[ANONIMIZADO]", solicitacao.getJustificativa());
        assertEquals("[ANONIMIZADO]", solicitacao.getParecer());
        assertEquals("[ANONIMIZADO]", solicitacao.getHistorico().getFirst().getObservacao());
        assertTrue(solicitacao.isAnonimizado());
        verify(solicitacaoRepository).save(solicitacao);
    }

    @Test
    void executar_mesmaSolicitacaoNasDuasConsultas_deveSalvarUmaVez() {
        Solicitacao solicitacao = criarSolicitacao();
        when(solicitacaoRepository.findParaAnonimizar(any())).thenReturn(List.of(solicitacao));
        when(solicitacaoRepository.findAntigasParaAnonimizar(any())).thenReturn(List.of(solicitacao));

        job().executar();

        verify(solicitacaoRepository, times(1)).save(solicitacao);
    }

    private Solicitacao criarSolicitacao() {
        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setAlunoNome("Maria Silva");
        solicitacao.setAlunoEmail("maria@teste.edu.br");
        solicitacao.setAlunoWhatsapp("65999999999");
        solicitacao.setDisciplina("Banco de Dados");
        solicitacao.setProfessor("Professor Teste");
        solicitacao.setDataOcorrencia(LocalDate.of(2026, 4, 10));
        solicitacao.setNotaAtual("6,0");
        solicitacao.setNotaEsperada("8,0");
        solicitacao.setJustificativa("Justificativa pessoal");
        solicitacao.setParecer("Parecer público");
        solicitacao.setStatus(StatusSolicitacao.RESOLVIDO);

        HistoricoSolicitacao historico = new HistoricoSolicitacao();
        historico.setObservacao("Observação com dados do aluno");
        solicitacao.setHistorico(new java.util.ArrayList<>(List.of(historico)));
        return solicitacao;
    }
}
