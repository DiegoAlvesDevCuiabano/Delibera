package com.delibera.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renderiza de fato as páginas autenticadas. A suíte anterior só checava status de
 * rotas públicas, então o fragment {@code sidebar :: topnav(null)} quebrava toda a
 * área ROOT em runtime com o build verde.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class PaginasAutenticadasTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "contato@appdelibera.com.br", roles = "ROOT")
    void rootPainel_deveRenderizar() throws Exception {
        mockMvc.perform(get("/root/painel")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "contato@appdelibera.com.br", roles = "ROOT")
    void rootErros_deveRenderizar() throws Exception {
        mockMvc.perform(get("/root/erros")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void dashboardCoordenacao_deveRenderizar() throws Exception {
        mockMvc.perform(get("/coordenacao")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void analisarSolicitacao_deveRenderizar() throws Exception {
        mockMvc.perform(get("/coordenacao/solicitacoes/1")).andExpect(status().isOk());
    }

    // ── /painel: era 500 servido a anônimo ──

    @Test
    void painel_semLogin_deveRedirecionarParaLogin() throws Exception {
        mockMvc.perform(get("/painel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "coordenacao@exemplo.edu.br", roles = "COORDENADOR")
    void painel_comCoordenador_deveIrParaCoordenacao() throws Exception {
        mockMvc.perform(get("/painel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/coordenacao"));
    }

    @Test
    @WithMockUser(username = "contato@appdelibera.com.br", roles = "ROOT")
    void painel_comRoot_deveIrParaPainelRoot() throws Exception {
        mockMvc.perform(get("/painel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/root/painel"));
    }

    // ── Default-deny: rota inexistente nao pode ser publica por omissao ──

    @Test
    void rotaDesconhecida_semLogin_naoDeveSerServida() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe-ainda"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
