package com.delibera.security;

import com.delibera.controller.VoucherController;
import com.delibera.model.entity.Instituicao;
import com.delibera.repository.InstituicaoRepository;
import com.delibera.util.TokenAssinado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O voucher era ativável por qualquer anônimo com {@code GET /voucher/ativar?inst=N}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class VoucherSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private TokenAssinado tokenAssinado;

    private Long instId;

    @BeforeEach
    void criarInstituicaoComPilotoExpirado() {
        Instituicao inst = new Instituicao("Faculdade do Teste de Voucher", "FTV");
        inst.setPilotoExpiraEm(LocalDateTime.now().minusDays(3));
        inst.setVoucherAtivadoEm(null);
        instId = instituicaoRepository.save(inst).getId();
    }

    private String tokenValido() {
        return tokenAssinado.gerar(VoucherController.RECURSO, instId, 7);
    }

    private boolean voucherFoiAtivado() {
        return instituicaoRepository.findById(instId).orElseThrow().getVoucherAtivadoEm() != null;
    }

    @Test
    void enumeracaoPorId_naoDeveAtivarNada() throws Exception {
        for (long id = 1; id <= 12; id++) {
            mockMvc.perform(get("/voucher/ativar").param("inst", String.valueOf(id)));
        }
        assertThat(voucherFoiAtivado()).isFalse();
    }

    @Test
    void getComTokenValido_mostraConfirmacaoSemAtivar() throws Exception {
        mockMvc.perform(get("/voucher/ativar").param("t", tokenValido()))
                .andExpect(status().isOk());
        assertThat(voucherFoiAtivado())
                .as("GET nao pode consumir o voucher — scanners de link seguem a URL sozinhos")
                .isFalse();
    }

    @Test
    void postComTokenValido_ativa() throws Exception {
        mockMvc.perform(post("/voucher/ativar").param("t", tokenValido()).with(csrf()))
                .andExpect(status().isOk());
        assertThat(voucherFoiAtivado()).isTrue();
    }

    @Test
    void postComTokenReutilizado_naoAtivaDeNovo() throws Exception {
        String t = tokenValido();
        mockMvc.perform(post("/voucher/ativar").param("t", t).with(csrf()));
        LocalDateTime primeiraAtivacao =
                instituicaoRepository.findById(instId).orElseThrow().getVoucherAtivadoEm();

        mockMvc.perform(post("/voucher/ativar").param("t", t).with(csrf()))
                .andExpect(status().isOk());

        assertThat(instituicaoRepository.findById(instId).orElseThrow().getVoucherAtivadoEm())
                .isEqualTo(primeiraAtivacao);
    }

    @Test
    void postComTokenAdulterado_naoAtiva() throws Exception {
        String t = tokenValido();
        String adulterado = t.substring(0, t.lastIndexOf('.')) + ".XXXXXXXXXXXXXXXXXXXXXXXXXXXX";
        mockMvc.perform(post("/voucher/ativar").param("t", adulterado).with(csrf()))
                .andExpect(status().isOk());
        assertThat(voucherFoiAtivado()).isFalse();
    }

    @Test
    void postComTokenExpirado_naoAtiva() throws Exception {
        String expirado = tokenAssinado.gerar(VoucherController.RECURSO, instId, -1);
        mockMvc.perform(post("/voucher/ativar").param("t", expirado).with(csrf()))
                .andExpect(status().isOk());
        assertThat(voucherFoiAtivado()).isFalse();
    }

    @Test
    void postSemToken_naoAtiva() throws Exception {
        mockMvc.perform(post("/voucher/ativar").with(csrf())).andExpect(status().isOk());
        assertThat(voucherFoiAtivado()).isFalse();
    }
}
