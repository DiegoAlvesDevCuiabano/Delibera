package com.delibera.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenAssinadoTest {

    private final TokenAssinado token = new TokenAssinado("segredo-de-teste-com-tamanho-ok", "LOCAL");

    @Test
    void gerarEValidar_deveDevolverOId() {
        String t = token.gerar("voucher", 42L, 7);
        assertThat(token.validar("voucher", t)).isEqualTo(42L);
    }

    @Test
    void tokenAdulterado_deveSerRejeitado() {
        String t = token.gerar("voucher", 42L, 7);
        String adulterado = t.substring(0, t.lastIndexOf('.')) + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        assertThat(token.validar("voucher", adulterado)).isNull();
    }

    @Test
    void payloadTrocadoParaOutroId_deveSerRejeitado() {
        // Refaz o payload apontando para outra instituição, mantendo a assinatura antiga
        String t = token.gerar("voucher", 42L, 7);
        String assinatura = t.substring(t.lastIndexOf('.'));
        String outroPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("voucher:99:9999999999".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(token.validar("voucher", outroPayload + assinatura)).isNull();
    }

    @Test
    void tokenExpirado_deveSerRejeitado() {
        assertThat(token.validar("voucher", token.gerar("voucher", 42L, -1))).isNull();
    }

    @Test
    void tokenDeOutroRecurso_deveSerRejeitado() {
        assertThat(token.validar("voucher", token.gerar("parecer", 42L, 7))).isNull();
    }

    @Test
    void entradasMalformadas_devemSerRejeitadas() {
        assertThat(token.validar("voucher", null)).isNull();
        assertThat(token.validar("voucher", "")).isNull();
        assertThat(token.validar("voucher", "sem-ponto")).isNull();
        assertThat(token.validar("voucher", "..")).isNull();
        assertThat(token.validar("voucher", "!!!.###")).isNull();
    }

    @Test
    void segredosDiferentes_naoValidamOTokenDoOutro() {
        TokenAssinado outro = new TokenAssinado("um-segredo-completamente-diferente", "LOCAL");
        assertThat(outro.validar("voucher", token.gerar("voucher", 42L, 7))).isNull();
    }

    @Test
    void producaoSemSegredo_naoDeveSubir() {
        assertThatThrownBy(() -> new TokenAssinado("", "PROD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOKEN_SECRET");
    }
}
