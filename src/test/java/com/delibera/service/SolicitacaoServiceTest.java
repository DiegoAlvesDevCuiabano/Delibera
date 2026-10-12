package com.delibera.service;

import com.delibera.exception.AccessDeniedException;
import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.Role;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.SolicitacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoServiceTest {

    @Mock
    private SolicitacaoRepository repository;

    @InjectMocks
    private SolicitacaoService service;

    private Instituicao inst;
    private Usuario coordenador;
    private Solicitacao solicitacao;

    @BeforeEach
    void setUp() {
        inst = new Instituicao("Faculdade Teste", "FT");
        inst.setId(1L);

        coordenador = new Usuario("Coord", "coord@teste.edu.br", "hash", Role.COORDENADOR, inst);
        coordenador.setId(1L);

        solicitacao = new Solicitacao();
        solicitacao.setId(1L);
        solicitacao.setProtocolo("DEL-2026-000001");
        solicitacao.setTipo(TipoSolicitacao.ABONO_FALTA);
        solicitacao.setStatus(StatusSolicitacao.EM_ANALISE);
        solicitacao.setAlunoNome("Aluno Teste");
        solicitacao.setAlunoEmail("aluno@teste.edu.br");
        solicitacao.setDisciplina("Algoritmos");
        solicitacao.setJustificativa("Consulta médica");
        solicitacao.setInstituicao(inst);
    }

    @Test
    void criar_deveGerarProtocoloEStatus() {
        when(repository.findByProtocolo(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao s = new Solicitacao();
        s.setTipo(TipoSolicitacao.ABONO_FALTA);
        s.setAlunoNome("Teste");
        s.setAlunoEmail("teste@test.com");
        s.setDisciplina("Math");
        s.setJustificativa("Motivo");

        Solicitacao resultado = service.criar(s, inst);

        assertNotNull(resultado.getProtocolo());
        assertTrue(resultado.getProtocolo().startsWith("DEL-"));
        assertEquals(StatusSolicitacao.EM_ANALISE, resultado.getStatus());
        assertEquals(inst, resultado.getInstituicao());
        assertFalse(resultado.getHistorico().isEmpty());
    }

    // ── Maquina de estados: RESOLVIDO/NEGADO/CANCELADA sao finais ──
    // Sem estes guards dava para deferir duas vezes, ou deferir o que o aluno ja
    // havia cancelado — cada repeticao mandando um novo email ao aluno.

    private void comStatus(StatusSolicitacao status) {
        solicitacao.setStatus(status);
        when(repository.findByIdAndInstituicaoIdForUpdate(1L, 1L)).thenReturn(Optional.of(solicitacao));
    }

    @Test
    void deferir_solicitacaoJaResolvida_deveFalhar() {
        comStatus(StatusSolicitacao.RESOLVIDO);
        assertThrows(IllegalStateException.class,
                () -> service.deferir(1L, "De novo", null, coordenador));
        verify(repository, never()).save(any());
    }

    @Test
    void deferir_solicitacaoCanceladaPeloAluno_deveFalhar() {
        comStatus(StatusSolicitacao.CANCELADA);
        assertThrows(IllegalStateException.class,
                () -> service.deferir(1L, "Deferido", null, coordenador));
        verify(repository, never()).save(any());
    }

    @Test
    void indeferir_solicitacaoJaNegada_deveFalhar() {
        comStatus(StatusSolicitacao.NEGADO);
        assertThrows(IllegalStateException.class,
                () -> service.indeferir(1L, "De novo", null, coordenador));
        verify(repository, never()).save(any());
    }

    @Test
    void encaminhar_solicitacaoJaResolvida_deveFalhar() {
        comStatus(StatusSolicitacao.RESOLVIDO);
        assertThrows(IllegalStateException.class,
                () -> service.encaminhar(1L, "Encaminhar", coordenador));
        verify(repository, never()).save(any());
    }

    @Test
    void deferir_aguardandoProfessor_deveSerPermitido() {
        comStatus(StatusSolicitacao.AGUARDANDO_PROFESSOR);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(StatusSolicitacao.RESOLVIDO,
                service.deferir(1L, "Deferido apos parecer", null, coordenador).getStatus());
    }

    @Test
    void deferir_deveMudarStatusParaResolvido() {
        when(repository.findByIdAndInstituicaoIdForUpdate(1L, 1L)).thenReturn(Optional.of(solicitacao));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao resultado = service.deferir(1L, "Deferido", "Nota interna", coordenador);

        assertEquals(StatusSolicitacao.RESOLVIDO, resultado.getStatus());
        assertEquals("Deferido", resultado.getParecer());
        assertEquals("Nota interna", resultado.getParecerInterno());
    }

    @Test
    void indeferir_deveMudarStatusParaNegado() {
        when(repository.findByIdAndInstituicaoIdForUpdate(1L, 1L)).thenReturn(Optional.of(solicitacao));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao resultado = service.indeferir(1L, "Indeferido", null, coordenador);

        assertEquals(StatusSolicitacao.NEGADO, resultado.getStatus());
    }

    @Test
    void encaminhar_deveMudarStatusParaAguardandoProfessor() {
        when(repository.findByIdAndInstituicaoIdForUpdate(1L, 1L)).thenReturn(Optional.of(solicitacao));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao resultado = service.encaminhar(1L, "Encaminhado", coordenador);

        assertEquals(StatusSolicitacao.AGUARDANDO_PROFESSOR, resultado.getStatus());
    }

    @Test
    void deferir_deveRejeitarInstituicaoDiferente() {
        // Coordenador da inst 1 tentando deferir solicitação da inst 999
        when(repository.findByIdAndInstituicaoIdForUpdate(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.deferir(99L, "Deferido", null, coordenador));
    }

    @Test
    void deferir_devePermitirRoot() {
        Usuario root = new Usuario("Root", "contato@appdelibera.com.br", "hash", Role.ROOT);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(solicitacao));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao resultado = service.deferir(1L, "Deferido pelo ROOT", null, root);

        assertEquals(StatusSolicitacao.RESOLVIDO, resultado.getStatus());
    }

    @Test
    void encaminhar_deveRejeitarInstituicaoDiferente() {
        when(repository.findByIdAndInstituicaoIdForUpdate(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.encaminhar(99L, "Encaminhado", coordenador));
    }

    @Test
    void indeferir_deveRejeitarInstituicaoDiferente() {
        when(repository.findByIdAndInstituicaoIdForUpdate(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.indeferir(99L, "Indeferido", null, coordenador));
    }

    @Test
    void buscarPorProtocoloEEmail_deveRetornarQuandoAmbosCorretos() {
        when(repository.findByProtocoloAndEmail("DEL-2026-000001", "aluno@teste.edu.br"))
                .thenReturn(Optional.of(solicitacao));

        Solicitacao resultado = service.buscarPorProtocoloEEmail("DEL-2026-000001", "aluno@teste.edu.br");
        assertEquals("DEL-2026-000001", resultado.getProtocolo());
    }

    @Test
    void buscarPorProtocoloEEmail_deveFalharComEmailErrado() {
        when(repository.findByProtocoloAndEmail("DEL-2026-000001", "outro@email.com"))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buscarPorProtocoloEEmail("DEL-2026-000001", "outro@email.com"));
        assertEquals("Protocolo ou email inválidos", ex.getMessage());
    }

    @Test
    void buscarPorProtocoloEEmail_deveNormalizarInputs() {
        when(repository.findByProtocoloAndEmail("DEL-2026-000001", "aluno@teste.edu.br"))
                .thenReturn(Optional.of(solicitacao));

        // Espaços e case diferente
        Solicitacao resultado = service.buscarPorProtocoloEEmail("  del-2026-000001  ", "  ALUNO@TESTE.EDU.BR  ");
        assertEquals("DEL-2026-000001", resultado.getProtocolo());
    }

    @Test
    void verificarOwnership_devePermitirRoot() {
        Usuario root = new Usuario("Root", "contato@appdelibera.com.br", "hash", Role.ROOT);
        Instituicao outraInst = new Instituicao("Outra", "O");
        outraInst.setId(999L);
        solicitacao.setInstituicao(outraInst);

        assertDoesNotThrow(() -> service.verificarOwnership(solicitacao, root));
    }

    @Test
    void verificarOwnership_deveRejeitarInstituicaoDiferente() {
        Instituicao outraInst = new Instituicao("Outra", "O");
        outraInst.setId(999L);
        solicitacao.setInstituicao(outraInst);

        assertThrows(AccessDeniedException.class,
                () -> service.verificarOwnership(solicitacao, coordenador));
    }

    // --- Novos testes ---

    @Test
    void listarPorEmail_deveDelegarAoRepository() {
        List<Solicitacao> lista = List.of(solicitacao);
        when(repository.findByAlunoEmailOrderByCriadoEmDesc("aluno@teste.edu.br"))
                .thenReturn(lista);

        List<Solicitacao> resultado = service.listarPorEmail("aluno@teste.edu.br");

        assertEquals(1, resultado.size());
        assertEquals(solicitacao, resultado.get(0));
        verify(repository).findByAlunoEmailOrderByCriadoEmDesc("aluno@teste.edu.br");
    }

    @Test
    void listarPorInstituicao_deveDelegarAoRepository() {
        List<Solicitacao> lista = List.of(solicitacao);
        when(repository.findByInstituicaoIdOrderByCriadoEmDesc(1L)).thenReturn(lista);

        List<Solicitacao> resultado = service.listarPorInstituicao(1L);

        assertEquals(1, resultado.size());
        verify(repository).findByInstituicaoIdOrderByCriadoEmDesc(1L);
    }

    @Test
    void listarPendentesPorInstituicao_devePassarStatusCorretos() {
        List<Solicitacao> lista = List.of(solicitacao);
        when(repository.findPendentesByInstituicao(eq(1L), any())).thenReturn(lista);

        List<Solicitacao> resultado = service.listarPendentesPorInstituicao(1L);

        assertEquals(1, resultado.size());
        verify(repository).findPendentesByInstituicao(eq(1L),
                eq(List.of(StatusSolicitacao.EM_ANALISE, StatusSolicitacao.AGUARDANDO_PROFESSOR)));
    }

    @Test
    void buscarPorId_encontrado_deveRetornarSolicitacao() {
        solicitacao.setHistorico(new ArrayList<>());
        when(repository.findById(1L)).thenReturn(Optional.of(solicitacao));

        Solicitacao resultado = service.buscarPorId(1L);

        assertEquals(1L, resultado.getId());
        verify(repository).findById(1L);
    }

    @Test
    void buscarPorId_naoEncontrado_deveLancarExcecao() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buscarPorId(999L));
        assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    void buscarPorProtocolo_encontrado_deveRetornarSolicitacao() {
        solicitacao.setHistorico(new ArrayList<>());
        when(repository.findByProtocolo("DEL-2026-000001")).thenReturn(Optional.of(solicitacao));

        Solicitacao resultado = service.buscarPorProtocolo("DEL-2026-000001");

        assertEquals("DEL-2026-000001", resultado.getProtocolo());
        verify(repository).findByProtocolo("DEL-2026-000001");
    }

    @Test
    void buscarPorProtocolo_naoEncontrado_deveLancarExcecao() {
        when(repository.findByProtocolo("DEL-INEXISTENTE")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buscarPorProtocolo("DEL-INEXISTENTE"));
        assertTrue(ex.getMessage().contains("DEL-INEXISTENTE"));
    }

    @Test
    void contarTotal_deveDelegarAoRepository() {
        when(repository.countByInstituicaoId(1L)).thenReturn(42L);

        long total = service.contarTotal(1L);

        assertEquals(42L, total);
        verify(repository).countByInstituicaoId(1L);
    }

    @Test
    void contarPorStatus_deveDelegarAoRepository() {
        when(repository.countByInstituicaoIdAndStatus(1L, StatusSolicitacao.EM_ANALISE)).thenReturn(5L);

        long count = service.contarPorStatus(1L, StatusSolicitacao.EM_ANALISE);

        assertEquals(5L, count);
        verify(repository).countByInstituicaoIdAndStatus(1L, StatusSolicitacao.EM_ANALISE);
    }

    @Test
    void listarComFiltros_semFiltros_naoEnviaParametrosNulosParaQuery() {
        PageRequest pagina = PageRequest.of(0, 20);
        when(repository.findByInstituicaoIdOrderByCriadoEmDesc(1L, pagina)).thenReturn(Page.empty());

        service.listarPorInstituicaoComFiltros(1L, null, null, pagina);

        verify(repository).findByInstituicaoIdOrderByCriadoEmDesc(1L, pagina);
        verify(repository, never()).findByInstituicaoAndBusca(any(), any(), any());
    }

    @Test
    void listarComFiltros_buscaEStatus_deveUsarQueryTipada() {
        PageRequest pagina = PageRequest.of(0, 20);
        when(repository.findByInstituicaoBuscaAndStatus(
                1L, "DEL-2026", StatusSolicitacao.EM_ANALISE, pagina)).thenReturn(Page.empty());

        service.listarPorInstituicaoComFiltros(
                1L, "  DEL-2026  ", StatusSolicitacao.EM_ANALISE, pagina);

        verify(repository).findByInstituicaoBuscaAndStatus(
                1L, "DEL-2026", StatusSolicitacao.EM_ANALISE, pagina);
    }

    @Test
    void buscarComOwnership_gestaoDeveIgnorarFiltroInstituicao() {
        Usuario gestao = new Usuario("Gestao", "gestao@appdelibera.com.br", "hash", Role.GESTAO);
        solicitacao.setHistorico(new ArrayList<>());
        when(repository.findById(1L)).thenReturn(Optional.of(solicitacao));

        Solicitacao resultado = service.buscarComOwnership(1L, gestao);

        assertEquals(1L, resultado.getId());
        verify(repository).findById(1L);
    }

    @Test
    void verificarOwnership_semInstituicao_deveLancarAccessDenied() {
        Usuario semInst = new Usuario("Sem Inst", "sem@teste.edu.br", "hash", Role.COORDENADOR);
        // usuario sem instituicao vinculada

        assertThrows(AccessDeniedException.class,
                () -> service.verificarOwnership(solicitacao, semInst));
    }
}
