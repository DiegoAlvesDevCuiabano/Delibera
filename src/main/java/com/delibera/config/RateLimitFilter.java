package com.delibera.config;

import com.delibera.util.IpExtractor;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro global que bloqueia IPs em rate limit para rotas públicas sensíveis.
 * Protege: /login, /consultar-protocolo, /parecer-professor, /suporte
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private final GlobalRateLimiter rateLimiter;

    public RateLimitFilter(GlobalRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        if ("POST".equalsIgnoreCase(httpReq.getMethod()) && isRotaProtegida(httpReq.getRequestURI())) {
            String ip = IpExtractor.getClientIp(httpReq);
            if (rateLimiter.isBlocked(ip)) {
                long minutos = rateLimiter.getMinutosRestantes(ip);
                if ("/login".equals(httpReq.getRequestURI())) {
                    ((HttpServletResponse) response).sendRedirect("/login?blocked=true");
                } else {
                    httpReq.setAttribute("minutosRestantes", minutos);
                    httpReq.getRequestDispatcher("/rate-limit-consulta").forward(request, response);
                }
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isRotaProtegida(String uri) {
        return "/login".equals(uri)
                || "/nova-solicitacao".equals(uri)
                || "/consultar-protocolo".equals(uri)
                || "/acompanhar".equals(uri)
                || "/parecer-professor".equals(uri)
                || "/parecer-professor/responder".equals(uri)
                || "/suporte".equals(uri)
                || "/cancelar-solicitacao".equals(uri);
    }
}
