package com.delibera.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginRateLimiter rateLimiter;

    public LoginFailureHandler(LoginRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String ip = getClientIp(request);
        rateLimiter.registrarFalha(ip);

        if (rateLimiter.isBlocked(ip)) {
            response.sendRedirect("/login?blocked=true");
        } else {
            int restantes = rateLimiter.getTentativasRestantes(ip);
            response.sendRedirect("/login?error=true&remaining=" + restantes);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
