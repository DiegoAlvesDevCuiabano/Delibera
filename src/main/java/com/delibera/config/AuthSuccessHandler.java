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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

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
