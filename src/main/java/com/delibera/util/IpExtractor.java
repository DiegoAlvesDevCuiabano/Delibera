package com.delibera.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extrai o IP do cliente para rate limiting e auditoria.
 *
 * <p>NÃO lê {@code X-Forwarded-For} diretamente: qualquer cliente pode forjar esse
 * header e zerar o rate limiting trocando o valor a cada requisição. O tratamento
 * do header é responsabilidade do {@code ForwardedHeaderFilter} do Spring, ativado
 * por {@code server.forward-headers-strategy=framework} — ele só roda na borda,
 * a partir do proxy da plataforma, e já entrega o {@code remoteAddr} correto.
 *
 * <p>Consequência operacional: em produção atrás de proxy, a propriedade
 * {@code server.forward-headers-strategy} precisa estar definida, senão todos os
 * clientes são contabilizados sob o IP do proxy.
 */
public final class IpExtractor {

    private IpExtractor() {}

    public static String getClientIp(HttpServletRequest request) {
        String remoto = request.getRemoteAddr();
        return (remoto == null || remoto.isBlank()) ? "desconhecido" : remoto;
    }
}
