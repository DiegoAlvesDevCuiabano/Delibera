package com.delibera.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Token opaco assinado (HMAC-SHA256) para links enviados por email.
 *
 * <p>Formato: {@code base64url(recurso:id:expiraEpochSec) + "." + base64url(hmac)}.
 * O id nunca aparece em claro num parâmetro adivinhável, a assinatura impede
 * forjar e o prazo limita a janela de uso de um link vazado.
 *
 * <p>O segredo vem de {@code delibera.token.secret}. Em produção é obrigatório
 * defini-lo; sem isso a aplicação não sobe, porque um segredo default tornaria
 * todo token forjável por quem tem acesso ao código.
 */
@Component
public class TokenAssinado {

    private static final String ALGORITMO = "HmacSHA256";
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] segredo;

    public TokenAssinado(@Value("${delibera.token.secret:}") String secret,
                         @Value("${delibera.ambiente:LOCAL}") String ambiente) {
        if (secret == null || secret.isBlank()) {
            if ("PROD".equalsIgnoreCase(ambiente)) {
                throw new IllegalStateException(
                        "delibera.token.secret é obrigatório em produção — defina a variável TOKEN_SECRET");
            }
            // Fora de produção, gera um segredo efêmero por boot: links não sobrevivem
            // a um restart, o que é aceitável em dev e evita um default publicado.
            byte[] aleatorio = new byte[32];
            new java.security.SecureRandom().nextBytes(aleatorio);
            this.segredo = aleatorio;
        } else {
            this.segredo = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Gera um token para {@code recurso}/{@code id} válido por {@code validadeDias}. */
    public String gerar(String recurso, Long id, int validadeDias) {
        return gerarPara(recurso, String.valueOf(id), validadeDias);
    }

    /**
     * Variante com sujeito textual — usada pelo fluxo do aluno, cuja identidade é o
     * email e não um id de entidade.
     *
     * <p>O sujeito não pode conter {@code :}, que é o separador do payload. Emails
     * válidos não contêm, mas a checagem evita que um sujeito forjado desloque os
     * campos e faça um token de um recurso passar por outro.
     */
    public String gerarPara(String recurso, String sujeito, int validadeDias) {
        if (sujeito == null || sujeito.contains(":")) {
            throw new IllegalArgumentException("Sujeito de token inválido");
        }
        long expira = Instant.now().plusSeconds((long) validadeDias * 86400).getEpochSecond();
        String payload = recurso + ":" + sujeito + ":" + expira;
        String payloadB64 = ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return payloadB64 + "." + ENC.encodeToString(assinar(payloadB64));
    }

    /**
     * Valida assinatura e prazo e devolve o sujeito embutido.
     *
     * @return o sujeito, ou {@code null} se inválido, adulterado ou expirado.
     */
    public String validarSujeito(String recurso, String token) {
        String[] partes = partesValidas(recurso, token);
        return partes == null ? null : partes[1];
    }

    /**
     * Valida assinatura e prazo e devolve o id embutido.
     *
     * @return o id, ou {@code null} se o token for inválido, adulterado ou expirado.
     */
    public Long validar(String recurso, String token) {
        String[] partes = partesValidas(recurso, token);
        if (partes == null) {
            return null;
        }
        try {
            return Long.parseLong(partes[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return {recurso, sujeito, expiracao} se tudo confere, senão null. */
    private String[] partesValidas(String recurso, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int ponto = token.lastIndexOf('.');
        if (ponto <= 0 || ponto == token.length() - 1) {
            return null;
        }

        String payloadB64 = token.substring(0, ponto);
        byte[] assinaturaRecebida;
        String payload;
        try {
            assinaturaRecebida = DEC.decode(token.substring(ponto + 1));
            payload = new String(DEC.decode(payloadB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }

        // Comparação em tempo constante — evita distinguir assinaturas por timing.
        if (!MessageDigest.isEqual(assinaturaRecebida, assinar(payloadB64))) {
            return null;
        }

        String[] partes = payload.split(":");
        if (partes.length != 3 || !recurso.equals(partes[0])) {
            return null;
        }

        try {
            if (Instant.now().getEpochSecond() > Long.parseLong(partes[2])) {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return partes;
    }

    private byte[] assinar(String payloadB64) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo, ALGORITMO));
            return mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar token", e);
        }
    }
}
