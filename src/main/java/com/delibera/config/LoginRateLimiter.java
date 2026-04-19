package com.delibera.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter para tentativas de login.
 * 5 tentativas em 15 minutos → bloqueio de 30 minutos por IP.
 */
@Component
public class LoginRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final int MAX_TENTATIVAS = 5;
    private static final int JANELA_MINUTOS = 15;
    private static final int BLOQUEIO_MINUTOS = 30;

    private final Map<String, LoginAttempt> tentativas = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        LoginAttempt attempt = tentativas.get(ip);
        if (attempt == null) return false;

        // Limpar expirados
        if (attempt.bloqueadoAte != null && attempt.bloqueadoAte.isBefore(LocalDateTime.now())) {
            tentativas.remove(ip);
            return false;
        }

        return attempt.bloqueadoAte != null && attempt.bloqueadoAte.isAfter(LocalDateTime.now());
    }

    public void registrarFalha(String ip) {
        tentativas.compute(ip, (key, attempt) -> {
            if (attempt == null) {
                attempt = new LoginAttempt();
            }

            // Reset se a janela expirou
            if (attempt.primeiroTentativa.plusMinutes(JANELA_MINUTOS).isBefore(LocalDateTime.now())) {
                attempt = new LoginAttempt();
            }

            attempt.contagem++;

            if (attempt.contagem >= MAX_TENTATIVAS) {
                attempt.bloqueadoAte = LocalDateTime.now().plusMinutes(BLOQUEIO_MINUTOS);
                logger.warn("[RATE LIMIT] IP {} bloqueado por {} minutos após {} tentativas", ip, BLOQUEIO_MINUTOS, attempt.contagem);
            }

            return attempt;
        });
    }

    public void registrarSucesso(String ip) {
        tentativas.remove(ip);
    }

    public int getTentativasRestantes(String ip) {
        LoginAttempt attempt = tentativas.get(ip);
        if (attempt == null) return MAX_TENTATIVAS;
        return Math.max(0, MAX_TENTATIVAS - attempt.contagem);
    }

    private static class LoginAttempt {
        int contagem = 0;
        LocalDateTime primeiroTentativa = LocalDateTime.now();
        LocalDateTime bloqueadoAte = null;
    }
}
