package com.delibera.security;

import com.delibera.config.CotaPublica;
import com.delibera.service.EmailHtmlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class HomologacaoSuporteTest {

    private static final String IP = "198.51.100.85";

    @Autowired private MockMvc mockMvc;
    @Autowired private CotaPublica cota;
    @MockitoBean private EmailHtmlService email;

    @AfterEach
    void limparCota() {
        cota.zerar(IP, "/suporte");
    }

    @Test
    void ajuda_abreComPerguntasFrequentes() throws Exception {
        mockMvc.perform(get("/suporte"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Perguntas frequentes")));
    }

    @Test
    void mensagemInvalida_naoEnfileiraEmail() throws Exception {
        mockMvc.perform(post("/suporte").with(csrf()).header("HX-Request", "true")
                        .with(r -> { r.setRemoteAddr(IP); return r; })
                        .param("nome", " ").param("email", "invalido")
                        .param("assunto", "Duvida").param("mensagem", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/suporte-resposta :: erro"));
        verifyNoInteractions(email);
    }

    @Test
    void suporte_exigeCsrf() throws Exception {
        mockMvc.perform(post("/suporte").param("nome", "Teste")
                        .param("email", "teste@example.test")
                        .param("assunto", "Duvida").param("mensagem", "Ajuda"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(email);
    }

    @Test
    void suporte_limitaEnviosPorIpSemInformarSucessoFalso() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/suporte").with(csrf()).header("HX-Request", "true")
                            .with(r -> { r.setRemoteAddr(IP); return r; })
                            .param("nome", "Teste").param("email", "teste@example.test")
                            .param("assunto", "Duvida").param("mensagem", "Preciso de ajuda"))
                    .andExpect(view().name("fragments/suporte-resposta :: sucesso"));
        }
        mockMvc.perform(post("/suporte").with(csrf()).header("HX-Request", "true")
                        .with(r -> { r.setRemoteAddr(IP); return r; })
                        .param("nome", "Teste").param("email", "teste@example.test")
                        .param("assunto", "Duvida").param("mensagem", "Preciso de ajuda"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/suporte-resposta :: erro"));
        verify(email, times(5)).enviarContatoSuporte(anyString(), anyString(), anyString(), anyString());
    }
}
