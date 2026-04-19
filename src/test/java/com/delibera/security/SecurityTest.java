package com.delibera.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
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

    // ── 404 para rotas inexistentes ──
    @Test
    void rotaInexistente_deveRetornar404() throws Exception {
        mockMvc.perform(get("/pagina-que-nao-existe"))
                .andExpect(status().isNotFound());
    }
}
