package com.delibera.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginRateLimiter rateLimiter;

    public AuthSuccessHandler(LoginRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        // Limpar rate limit no sucesso
        String ip = request.getHeader("X-Forwarded-For");
        rateLimiter.registrarSucesso(ip != null ? ip.split(",")[0].trim() : request.getRemoteAddr());

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");

        String redirectUrl = switch (role) {
            case "ROLE_ROOT", "ROLE_GESTAO" -> "/gestao";
            case "ROLE_ADMIN_INSTITUICAO", "ROLE_COORDENADOR" -> "/coordenacao";
            default -> "/";
        };

        response.sendRedirect(redirectUrl);
    }
}
