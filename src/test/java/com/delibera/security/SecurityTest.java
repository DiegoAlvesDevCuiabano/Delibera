package com.delibera.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Rotas públicas devem retornar 200 ──
    @Test
    void landingPage_deveRetornar200() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void loginPage_deveRetornar200() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void novaSolicitacao_deveRetornar200() throws Exception {
        mockMvc.perform(get("/nova-solicitacao")).andExpect(status().isOk());
    }

    @Test
    void consultarProtocolo_deveRetornar200() throws Exception {
        mockMvc.perform(get("/consultar-protocolo")).andExpect(status().isOk());
    }

    @Test
    void css_deveRetornar200() throws Exception {
        mockMvc.perform(get("/css/delibera.css")).andExpect(status().isOk());
    }

    @Test
    void js_deveRetornar200() throws Exception {
        mockMvc.perform(get("/js/delibera.js")).andExpect(status().isOk());
    }

    // ── Rotas protegidas devem redirecionar para login ──
    @Test
    void coordenacao_semLogin_deveRedirecionar() throws Exception {
        mockMvc.perform(get("/coordenacao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void admin_semLogin_deveRedirecionar() throws Exception {
        mockMvc.perform(get("/admin/algo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void root_semLogin_deveRedirecionar() throws Exception {
        mockMvc.perform(get("/root/algo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void feedback_semLogin_deveRedirecionar() throws Exception {
        mockMvc.perform(get("/feedback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(roles = "COORDENADOR")
    void feedback_semCsrf_deveRetornar403() throws Exception {
        mockMvc.perform(post("/feedback").param("nota", "5"))
                .andExpect(status().isForbidden());
    }

    // ── Rota desconhecida exige login (anyRequest authenticated) ──
    // Invertido de propósito: com `anyRequest().permitAll()` qualquer controller
    // novo nascia público — foi assim que /painel virou um 500 aberto a anônimos.
    @Test
    void rotaInexistente_deveExigirLogin() throws Exception {
        mockMvc.perform(get("/pagina-que-nao-existe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
