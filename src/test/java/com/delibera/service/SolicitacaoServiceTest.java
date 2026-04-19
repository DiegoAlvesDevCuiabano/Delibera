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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void deferir_deveMudarStatusParaResolvido() {
        when(repository.findById(1L)).thenReturn(Optional.of(solicitacao));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao resultado = service.deferir(1L, "Deferido", coordenador);

        assertEquals(StatusSolicitacao.RESOLVIDO, resultado.getStatus());
        assertEquals("Deferido", resultado.getParecer());
    }

    @Test
    void indeferir_deveMudarStatusParaNegado() {
        when(repository.findById(1L)).thenReturn(Optional.of(solicitacao));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao resultado = service.indeferir(1L, "Indeferido", coordenador);

        assertEquals(StatusSolicitacao.NEGADO, resultado.getStatus());
    }

    @Test
    void encaminhar_deveMudarStatusParaAguardandoProfessor() {
        when(repository.findById(1L)).thenReturn(Optional.of(solicitacao));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Solicitacao resultado = service.encaminhar(1L, "Encaminhado", coordenador);

        assertEquals(StatusSolicitacao.AGUARDANDO_PROFESSOR, resultado.getStatus());
    }

    @Test
    void cancelar_deveRejeitarSolicitacaoFinalizada() {
        solicitacao.setStatus(StatusSolicitacao.RESOLVIDO);
        when(repository.findById(1L)).thenReturn(Optional.of(solicitacao));

        assertThrows(IllegalStateException.class,
                () -> service.cancelar(1L, "aluno@teste.edu.br"));
    }

    @Test
    void cancelar_deveRejeitarEmailDiferente() {
        when(repository.findById(1L)).thenReturn(Optional.of(solicitacao));

        assertThrows(IllegalArgumentException.class,
                () -> service.cancelar(1L, "outro@email.com"));
    }

    @Test
    void verificarOwnership_devePermitirRoot() {
        Usuario root = new Usuario("Root", "root@delibera.com.br", "hash", Role.ROOT);
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
}
