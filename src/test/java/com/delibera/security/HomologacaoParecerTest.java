package com.delibera.security;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.ParecerProfessor;
import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.RespostaProfessor;
import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.repository.ParecerProfessorRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class HomologacaoParecerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InstituicaoRepository instituicoes;
    @Autowired private SolicitacaoRepository solicitacoes;
    @Autowired private ParecerProfessorRepository pareceres;

    private ParecerProfessor criarParecer(StatusSolicitacao status) {
        Instituicao inst = instituicoes.findAll().stream()
                .filter(i -> "Faculdade Exemplo".equals(i.getNome())).findFirst().orElseThrow();
        String sufixo = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Solicitacao s = new Solicitacao();
        s.setProtocolo("DEL-PRF-" + sufixo);
        s.setTipo(TipoSolicitacao.ABONO_FALTA);
        s.setStatus(status);
        s.setAlunoNome("Aluno do parecer");
        s.setAlunoEmail("parecer@example.test");
        s.setDisciplina("Disciplina");
        s.setJustificativa("Teste de resposta");
        s.setInstituicao(inst);
        s.adicionarHistorico(null, status, "Criada no teste");
        solicitacoes.save(s);

        ParecerProfessor parecer = new ParecerProfessor();
        parecer.setSolicitacao(s);
        parecer.setEmailProfessor("professor@example.test");
        parecer.setCodigoAcesso("PRF-" + sufixo);
        return pareceres.save(parecer);
    }

    @Test
    void respostaValida_alteraEstadoEImpedeReusoDoCodigo() throws Exception {
        ParecerProfessor parecer = criarParecer(StatusSolicitacao.AGUARDANDO_PROFESSOR);
        mockMvc.perform(post("/parecer-professor")
                        .param("protocolo", parecer.getSolicitacao().getProtocolo())
                        .param("codigo", parecer.getCodigoAcesso()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/public/parecer-professor-resposta"));

        mockMvc.perform(post("/parecer-professor/responder")
                        .param("parecerId", parecer.getId().toString())
                        .param("codigoConfirmacao", parecer.getCodigoAcesso())
                        .param("resposta", RespostaProfessor.FEITO.name())
                        .param("comentario", "Confirmado").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/public/parecer-professor-sucesso"));
        assertThat(pareceres.findById(parecer.getId()).orElseThrow().isUsado()).isTrue();
        assertThat(solicitacoes.findById(parecer.getSolicitacao().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.EM_ANALISE);

        mockMvc.perform(post("/parecer-professor/responder")
                        .param("parecerId", parecer.getId().toString())
                        .param("codigoConfirmacao", parecer.getCodigoAcesso())
                        .param("resposta", RespostaProfessor.NAO_FEITO.name()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("erro"));
        assertThat(pareceres.findById(parecer.getId()).orElseThrow().getResposta())
                .isEqualTo(RespostaProfessor.FEITO);
    }

    @Test
    void respostaAposDecisaoFinal_naoReabreSolicitacao() throws Exception {
        ParecerProfessor parecer = criarParecer(StatusSolicitacao.RESOLVIDO);
        mockMvc.perform(post("/parecer-professor/responder")
                        .param("parecerId", parecer.getId().toString())
                        .param("codigoConfirmacao", parecer.getCodigoAcesso())
                        .param("resposta", RespostaProfessor.FEITO.name()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("erro"));
        assertThat(solicitacoes.findById(parecer.getSolicitacao().getId()).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacao.RESOLVIDO);
        assertThat(pareceres.findById(parecer.getId()).orElseThrow().isUsado()).isFalse();
    }

    @Test
    @WithMockUser(roles = "COORDENADOR")
    void respostaSemCsrf_eBloqueada() throws Exception {
        ParecerProfessor parecer = criarParecer(StatusSolicitacao.AGUARDANDO_PROFESSOR);
        mockMvc.perform(post("/parecer-professor/responder")
                        .param("parecerId", parecer.getId().toString())
                        .param("codigoConfirmacao", parecer.getCodigoAcesso())
                        .param("resposta", RespostaProfessor.FEITO.name()))
                .andExpect(status().isForbidden());
        assertThat(pareceres.findById(parecer.getId()).orElseThrow().isUsado()).isFalse();
    }
}
