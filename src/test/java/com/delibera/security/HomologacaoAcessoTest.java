package com.delibera.security;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Anexo;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.AnexoRepository;
import com.delibera.repository.SolicitacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class HomologacaoAcessoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InstituicaoRepository instituicoes;
    @Autowired private SolicitacaoRepository solicitacoes;
    @Autowired private AnexoRepository anexos;

    private Solicitacao criarEmOutroTenant() {
        Instituicao outra = instituicoes.save(new Instituicao("Instituicao Isolada", "ISOLADA"));
        Solicitacao s = new Solicitacao();
        s.setProtocolo("DEL-ISOL-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        s.setTipo(TipoSolicitacao.ABONO_FALTA);
        s.setStatus(StatusSolicitacao.EM_ANALISE);
        s.setAlunoNome("Aluno Sigiloso");
        s.setAlunoEmail("sigiloso@example.test");
        s.setDisciplina("Disciplina Sigilosa");
        s.setJustificativa("Conteudo de outro tenant");
        s.setInstituicao(outra);
        s.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Criada no teste");
        return solicitacoes.save(s);
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void coordenador_naoLeNemDecidePedidoDeOutraInstituicao() throws Exception {
        Solicitacao alheia = criarEmOutroTenant();
        mockMvc.perform(get("/coordenacao/solicitacoes/{id}", alheia.getId()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/coordenacao/solicitacoes/{id}/deferir", alheia.getId())
                        .param("parecer", "Tentativa indevida").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(solicitacoes.findById(alheia.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.EM_ANALISE);
        String dashboard = mockMvc.perform(get("/coordenacao/dashboard"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dashboard).doesNotContain(alheia.getProtocolo(), "Aluno Sigiloso");
        String csv = mockMvc.perform(get("/coordenacao/relatorio.csv"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).doesNotContain(alheia.getProtocolo(), "Aluno Sigiloso");
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void coordenador_semCsrf_naoAlteraPedido() throws Exception {
        Solicitacao propria = solicitacoes.findByProtocolo("DEL-2026-000001").orElseThrow();
        mockMvc.perform(post("/coordenacao/solicitacoes/{id}/deferir", propria.getId())
                        .param("parecer", "Sem CSRF"))
                .andExpect(status().isForbidden());
        assertThat(solicitacoes.findById(propria.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.EM_ANALISE);
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void coordenador_baixaAnexoProprioMasNaoDeOutroTenant() throws Exception {
        Solicitacao propria = solicitacoes.findByProtocolo("DEL-2026-000001").orElseThrow();
        Solicitacao alheia = criarEmOutroTenant();
        byte[] conteudo = "%PDF-1.4\nTeste".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String nome = "homologacao-" + java.util.UUID.randomUUID() + ".pdf";
        java.nio.file.Path arquivo = java.nio.file.Path.of("uploads", nome);
        java.nio.file.Files.createDirectories(arquivo.getParent());
        java.nio.file.Files.write(arquivo, conteudo);
        try {
            Anexo anexoProprio = anexos.save(new Anexo("proprio.pdf", nome,
                    "application/pdf", conteudo.length, propria));
            Anexo anexoAlheio = anexos.save(new Anexo("alheio.pdf", nome,
                    "application/pdf", conteudo.length, alheia));
            mockMvc.perform(get("/coordenacao/anexos/{id}/download", anexoProprio.getId()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/coordenacao/anexos/{id}/download", anexoAlheio.getId()))
                    .andExpect(status().isNotFound());
        } finally {
            java.nio.file.Files.deleteIfExists(arquivo);
        }
    }

    @Test
    @WithMockUser(username = "admin@exemplo.edu.br", roles = "ADMIN_INSTITUICAO")
    void adminInstituicao_acessaCoordenacaoMasNaoGestaoGlobal() throws Exception {
        mockMvc.perform(get("/coordenacao/dashboard")).andExpect(status().isOk());
        mockMvc.perform(get("/gestao/instituicoes")).andExpect(status().isForbidden());
        mockMvc.perform(get("/root/painel")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gestao@example.test", roles = "GESTAO")
    void gestao_acessaProvisionamentoMasNaoPainelRootNemCoordenacao() throws Exception {
        mockMvc.perform(get("/gestao/instituicoes")).andExpect(status().isOk());
        mockMvc.perform(get("/root/painel")).andExpect(status().isForbidden());
        mockMvc.perform(get("/coordenacao/dashboard")).andExpect(status().isForbidden());
    }
}
