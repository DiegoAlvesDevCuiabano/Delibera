package com.delibera.security;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.InstituicaoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class RelatorioCsvTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SolicitacaoRepository solicitacaoRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;

    private String baixar() throws Exception {
        return mockMvc.perform(get("/coordenacao/relatorio.csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void semLogin_deveRedirecionarParaLogin() throws Exception {
        mockMvc.perform(get("/coordenacao/relatorio.csv"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void cabecalhoELinhasDevemTerOMesmoNumeroDeColunas() throws Exception {
        String csv = baixar();
        String[] linhas = csv.split("\n");
        int colunasCabecalho = linhas[0].split(";", -1).length;

        assertThat(linhas.length).isGreaterThan(1);
        for (int i = 1; i < linhas.length; i++) {
            assertThat(contarCampos(linhas[i]))
                    .as("linha %d desalinhada do cabeçalho", i)
                    .isEqualTo(colunasCabecalho);
        }
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void naoDeveExporParecerInterno() throws Exception {
        assertThat(baixar()).doesNotContain("Nota interna");
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void naoDeveIncluirSolicitacaoDeOutraInstituicao() throws Exception {
        Instituicao outra = instituicaoRepository.save(new Instituicao("Faculdade Alheia CSV", "FAC"));
        Solicitacao s = new Solicitacao();
        s.setProtocolo("DEL-2026-CSVALHEIA");
        s.setTipo(TipoSolicitacao.ABONO_FALTA);
        s.setStatus(StatusSolicitacao.EM_ANALISE);
        s.setAlunoNome("Aluno De Outra Casa");
        s.setAlunoEmail("alheio.csv@teste.edu.br");
        s.setDisciplina("Disciplina");
        s.setJustificativa("Justificativa");
        s.setInstituicao(outra);
        s.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Criada no teste");
        solicitacaoRepository.save(s);

        assertThat(baixar()).doesNotContain("DEL-2026-CSVALHEIA", "Aluno De Outra Casa");
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void formulaEmCampoDeTextoLivreDeveSerNeutralizada() throws Exception {
        Instituicao inst = instituicaoRepository.findAll().stream()
                .filter(i -> "Faculdade Exemplo".equals(i.getNome()))
                .findFirst().orElseThrow();

        Solicitacao s = new Solicitacao();
        s.setProtocolo("DEL-2026-CSVFORMULA");
        s.setTipo(TipoSolicitacao.ABONO_FALTA);
        s.setStatus(StatusSolicitacao.EM_ANALISE);
        // Texto livre digitado por aluno anônimo: o Excel executa isto ao abrir.
        s.setAlunoNome("=HYPERLINK(\"http://malicioso\",\"clique\")");
        s.setAlunoEmail("formula@teste.edu.br");
        s.setDisciplina("Disciplina");
        s.setJustificativa("Justificativa");
        s.setInstituicao(inst);
        s.adicionarHistorico(null, StatusSolicitacao.EM_ANALISE, "Criada no teste");
        solicitacaoRepository.save(s);

        String csv = baixar();
        assertThat(csv).contains("\"'=HYPERLINK");
        assertThat(csv).doesNotContain(";\"=HYPERLINK");
    }

    private int contarCampos(String linha) {
        int campos = 1;
        boolean dentroDeAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if (c == ';' && !dentroDeAspas) {
                campos++;
            }
        }
        return campos;
    }
}
