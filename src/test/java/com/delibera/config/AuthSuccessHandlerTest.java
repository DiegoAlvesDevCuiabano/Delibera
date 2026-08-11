package com.delibera.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSuccessHandlerTest {

    private GlobalRateLimiter rateLimiter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private AuthSuccessHandler handler;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(GlobalRateLimiter.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        handler = new AuthSuccessHandler(rateLimiter);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void login_gestao_redirecionaParaGestao() throws Exception {
        Authentication auth = autenticacao("ROLE_GESTAO");

        handler.onAuthenticationSuccess(request, response, auth);

        verify(response).sendRedirect("/gestao");
    }

    @Test
    void login_adminInstituicao_redirecionaParaCoordenacao() throws Exception {
        Authentication auth = autenticacao("ROLE_ADMIN_INSTITUICAO");

        handler.onAuthenticationSuccess(request, response, auth);

        verify(response).sendRedirect("/coordenacao");
    }

    private Authentication autenticacao(String role) {
        return new UsernamePasswordAuthenticationToken(
                "usuario@exemplo.edu.br", "senha", List.of(new SimpleGrantedAuthority(role)));
    }
}
