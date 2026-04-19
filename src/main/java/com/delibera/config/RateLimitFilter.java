package com.delibera.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro que bloqueia POST /login de IPs em rate limit.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private final LoginRateLimiter rateLimiter;

    public RateLimitFilter(LoginRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        if ("POST".equalsIgnoreCase(httpReq.getMethod()) && "/login".equals(httpReq.getRequestURI())) {
            String ip = getClientIp(httpReq);
            if (rateLimiter.isBlocked(ip)) {
                ((HttpServletResponse) response).sendRedirect("/login?blocked=true");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
