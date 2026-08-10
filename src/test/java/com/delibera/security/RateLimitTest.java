package com.delibera.security;

import com.delibera.config.GlobalRateLimiter;
import com.delibera.repository.ErroLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class RateLimitTest {

    /** IP que o MockMvc atribui às requisições. */
    private static final String IP_TESTE = "127.0.0.1";

    @Autowired private MockMvc mockMvc;
    @Autowired private GlobalRateLimiter rateLimiter;
    @Autowired private ErroLogRepository erroLogRepository;

    /**
     * O rate limiter é um singleton compartilhado com os demais testes do contexto.
     * Sem zerar antes e depois, um teste bloqueia o IP e derruba o seguinte.
     */
    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void limparContadorDoIpDeTeste() {
        rateLimiter.registrarSucesso(IP_TESTE);
    }

    private org.springframework.test.web.servlet.ResultActions consultaInvalida(String xff) throws Exception {
        var req = post("/consultar-protocolo")
                .param("protocolo", "DEL-2026-INEXISTE")
                .param("email", "ninguem@exemplo.com")
                .with(csrf());
        if (xff != null) {
            req = req.header("X-Forwarded-For", xff);
        }
        return mockMvc.perform(req);
    }

    /**
     * O MockMvc não executa forwards de verdade (o MockRequestDispatcher apenas
     * registra o destino), então aqui verificamos que o filtro desviou a requisição.
     * O comportamento do destino é coberto por {@link #rateLimitConsulta_aceitaPost()}.
     */
    @Test
    void aposCincoFalhas_desviaParaPaginaDeRateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            consultaInvalida(null);
        }
        consultaInvalida(null)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .forwardedUrl("/rate-limit-consulta"));
    }

    /**
     * Regressão direta do bug: o forward preserva o POST e o handler só aceitava GET,
     * então toda requisição bloqueada estourava HttpRequestMethodNotSupportedException.
     */
    @Test
    void rateLimitConsulta_aceitaPost() throws Exception {
        mockMvc.perform(post("/rate-limit-consulta").with(csrf()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void rateLimitConsulta_aceitaGet() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/rate-limit-consulta"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requisicaoBloqueada_naoGeraErroLog() throws Exception {
        long antes = erroLogRepository.count();
        for (int i = 0; i < 10; i++) {
            consultaInvalida(null);
        }
        // Antes, cada request bloqueada estourava HttpRequestMethodNotSupportedException,
        // gravava um ErroLog e disparava um email de alerta.
        assertThat(erroLogRepository.count())
                .as("bloqueio nao pode virar amplificador de alerta")
                .isEqualTo(antes);
    }

    @Test
    void xForwardedForForjado_naoContornaOBloqueio() throws Exception {
        for (int i = 0; i < 5; i++) {
            consultaInvalida(null);
        }
        // Trocar o header a cada tentativa costumava zerar o contador
        for (int i = 1; i <= 5; i++) {
            consultaInvalida("203.0.113." + i)
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .forwardedUrl("/rate-limit-consulta"));
        }
    }

    @Test
    void limparExpirados_removeOciosasEMantemBloqueadas() {
        rateLimiter.registrarFalha("198.51.100.1", "/teste");
        for (int i = 0; i < 5; i++) {
            rateLimiter.registrarFalha("198.51.100.2", "/teste");
        }
        int antes = rateLimiter.tamanho();
        rateLimiter.limparExpirados();

        assertThat(rateLimiter.tamanho())
                .as("nada expirou ainda — nenhuma entrada deve sumir")
                .isEqualTo(antes);
        assertThat(rateLimiter.isBlocked("198.51.100.2")).isTrue();
    }
}
