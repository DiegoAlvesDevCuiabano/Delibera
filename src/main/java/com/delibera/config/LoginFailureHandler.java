package com.delibera.config;

import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final GlobalRateLimiter rateLimiter;

    public LoginFailureHandler(GlobalRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String ip = IpExtractor.getClientIp(request);
        rateLimiter.registrarFalha(ip, "/login");

        if (rateLimiter.isBlocked(ip)) {
            response.sendRedirect("/login?blocked=true");
        } else {
            int restantes = rateLimiter.getTentativasRestantes(ip);
            response.sendRedirect("/login?error=true&remaining=" + restantes);
        }
    }
}
