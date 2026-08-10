package com.delibera.security;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.SolicitacaoRepository;
import com.delibera.service.AcessoAlunoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Área do aluno por link assinado: lista, detalhe, portabilidade e eliminação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class AreaAlunoTest {

    private static final java.util.concurrent.atomic.AtomicInteger SEQUENCIA =
            new java.util.concurrent.atomic.AtomicInteger();

    private String email;
    private String emailAlheio;

    @Autowired private MockMvc mockMvc;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private AcessoAlunoService acessoAluno;

    private String protocoloResolvido;
    private String protocoloAlheio;

    @BeforeEach
    void criarDados() {
        // Emails únicos por teste: o contexto Spring e o banco H2 são compartilhados,
        // então reutilizar o mesmo email faria um teste enxergar o estado do anterior.
        String id = String.valueOf(System.nanoTime());
        email = "aluno." + id + "@teste.edu.br";
        emailAlheio = "outro." + id + "@teste.edu.br";

        instituicaoRepository.save(new Instituicao("Faculdade Area Aluno", "FAA"));
        protocoloResolvido = criar(email, StatusSolicitacao.RESOLVIDO, "AREA-RESOLVIDA");
        criar(email, StatusSolicitacao.EM_ANALISE, "AREA-ANDAMENTO");
        protocoloAlheio = criar(emailAlheio, StatusSolicitacao.RESOLVIDO, "AREA-ALHEIA");
    }

    private String criar(String emailDono, StatusSolicitacao status, String sufixo) {
        Instituicao inst = instituicaoRepository.findAll().get(0);
        Solicitacao s = new Solicitacao();
        // Contador, não nanoTime: `protocolo` é UNIQUE e duas chamadas seguidas caem
        // no mesmo valor truncado, quebrando o teste de forma intermitente.
        s.setProtocolo("DEL-2026-" + sufixo + "-" + SEQUENCIA.incrementAndGet());
        s.setTipo(TipoSolicitacao.ABONO_FALTA);
        s.setStatus(status);
        s.setAlunoNome("Aluno Teste");
        s.setAlunoEmail(emailDono);
        s.setDisciplina("Disciplina");
        s.setJustificativa("Justificativa");
        s.setInstituicao(inst);
        s.adicionarHistorico(null, status, "Criada no teste");
        return solicitacaoRepository.save(s).getProtocolo();
    }

    private String token() {
        return acessoAluno.gerarToken(email);
    }

    // ── Lista ────────────────────────────────────────────────────

    @Test
    void listar_comTokenValido_mostraSolicitacoesDoAluno() throws Exception {
        mockMvc.perform(get("/meus-pedidos").param("t", token()))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/aluno/acompanhar"));
    }

    @Test
    void listar_semToken_voltaParaConsulta() throws Exception {
        mockMvc.perform(get("/meus-pedidos"))
                .andExpect(view().name("pages/aluno/consultar-protocolo"));
    }

    @Test
    void listar_tokenAdulterado_voltaParaConsulta() throws Exception {
        String t = token();
        String adulterado = t.substring(0, t.lastIndexOf('.')) + ".XXXXXXXXXXXXXXXX";
        mockMvc.perform(get("/meus-pedidos").param("t", adulterado))
                .andExpect(view().name("pages/aluno/consultar-protocolo"));
    }

    // ── Detalhe: sobrevive a refresh e nao vaza protocolo alheio ──

    @Test
    void detalhe_comToken_renderizaEPodeSerRecarregado() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/meus-pedidos/" + protocoloResolvido).param("t", token()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("pages/aluno/status-solicitacao"));
        }
    }

    @Test
    void detalhe_protocoloDeOutroAluno_naoEhAcessivel() throws Exception {
        mockMvc.perform(get("/meus-pedidos/" + protocoloAlheio).param("t", token()))
                .andExpect(view().name("pages/aluno/consultar-protocolo"));
    }

    // ── Portabilidade ────────────────────────────────────────────

    @Test
    void exportar_comToken_devolveJsonComOsDados() throws Exception {
        mockMvc.perform(get("/meus-dados/exportar").param("t", token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(email)))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(emailAlheio))));
    }

    @Test
    void exportar_semToken_devolve403() throws Exception {
        mockMvc.perform(get("/meus-dados/exportar")).andExpect(status().isForbidden());
    }

    // ── Eliminação ───────────────────────────────────────────────

    @Test
    void excluir_marcaApenasAsFinalizadas() throws Exception {
        mockMvc.perform(post("/meus-dados/excluir").param("t", token()).with(csrf()))
                .andExpect(status().isOk());

        var doAluno = solicitacaoRepository.findByAlunoEmailOrderByCriadoEmDesc(email);
        assertThat(doAluno).filteredOn(s -> s.getStatus() == StatusSolicitacao.RESOLVIDO)
                .allMatch(s -> s.getExcluidoEm() != null);
        assertThat(doAluno).filteredOn(s -> s.getStatus() == StatusSolicitacao.EM_ANALISE)
                .as("solicitacao em andamento nao pode ser apagada no meio do processo")
                .allMatch(s -> s.getExcluidoEm() == null);
    }

    @Test
    void excluir_naoAfetaDadosDeOutroAluno() throws Exception {
        mockMvc.perform(post("/meus-dados/excluir").param("t", token()).with(csrf()));
        assertThat(solicitacaoRepository.findByAlunoEmailOrderByCriadoEmDesc(emailAlheio))
                .allMatch(s -> s.getExcluidoEm() == null);
    }

    @Test
    void excluir_semToken_naoMarcaNada() throws Exception {
        mockMvc.perform(post("/meus-dados/excluir").with(csrf()))
                .andExpect(view().name("pages/aluno/consultar-protocolo"));
        assertThat(solicitacaoRepository.findByAlunoEmailOrderByCriadoEmDesc(email))
                .allMatch(s -> s.getExcluidoEm() == null);
    }

    @Test
    void excluidas_somemDaListaEDoExport() throws Exception {
        mockMvc.perform(post("/meus-dados/excluir").param("t", token()).with(csrf()));
        mockMvc.perform(get("/meus-dados/exportar").param("t", token()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(protocoloResolvido))));
    }
}
