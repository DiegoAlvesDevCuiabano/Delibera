package com.delibera.service;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.SolicitacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class DecisaoConcorrenteTest {

    @Autowired private SolicitacaoService solicitacaoService;
    @Autowired private SolicitacaoRepository solicitacoes;
    @Autowired private UsuarioService usuarios;

    @Test
    void doisResponsaveis_decidemMesmoPedido_apenasUmaDecisaoPersiste() throws Exception {
        Usuario coordenador = usuarios.buscarPorEmail("coordenacao@exemplo.edu.br");
        Usuario administrador = usuarios.buscarPorEmail("admin@exemplo.edu.br");
        Instituicao instituicao = coordenador.getInstituicao();

        Solicitacao pedido = new Solicitacao();
        pedido.setProtocolo("DEL-RACE-" + UUID.randomUUID().toString().substring(0, 8));
        pedido.setTipo(TipoSolicitacao.ABONO_FALTA);
        pedido.setStatus(StatusSolicitacao.EM_ANALISE);
        pedido.setAlunoNome("Aluno concorrência");
        pedido.setAlunoEmail("corrida@example.test");
        pedido.setDisciplina("Disciplina");
        pedido.setJustificativa("Teste de decisão simultânea");
        pedido.setInstituicao(instituicao);
        pedido.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Criada no teste");
        Long id = solicitacoes.save(pedido).getId();

        CountDownLatch inicio = new CountDownLatch(1);
        Callable<Boolean> deferir = () -> decidir(inicio,
                () -> solicitacaoService.deferir(id, "Deferido", null, coordenador));
        Callable<Boolean> indeferir = () -> decidir(inicio,
                () -> solicitacaoService.indeferir(id, "Indeferido", null, administrador));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> resultados = List.of(executor.submit(deferir), executor.submit(indeferir));
            inicio.countDown();
            int sucessos = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) sucessos++;
            }
            assertThat(sucessos).isEqualTo(1);
        }

        Solicitacao finalizada = solicitacaoService.buscarPorId(id);
        assertThat(finalizada.getStatus()).isIn(StatusSolicitacao.RESOLVIDO, StatusSolicitacao.NEGADO);
        assertThat(finalizada.getHistorico()).hasSize(2);
    }

    private boolean decidir(CountDownLatch inicio, Runnable acao) throws InterruptedException {
        inicio.await();
        try {
            acao.run();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
