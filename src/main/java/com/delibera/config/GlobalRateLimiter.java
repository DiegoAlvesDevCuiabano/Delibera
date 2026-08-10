package com.delibera.config;

import com.delibera.model.entity.ErroLog;
import com.delibera.repository.ErroLogRepository;
import com.delibera.service.AlertaErroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter global com bloqueio escalável por IP.
 *
 * Nível 1: 5 falhas → bloqueio 15 min
 * Nível 2: 10 falhas acumuladas → bloqueio 1h
 * Nível 3: 20 falhas acumuladas → bloqueio 4h
 * 30+ falhas → alerta de possível ataque
 */
@Component
public class GlobalRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(GlobalRateLimiter.class);

    private static final int NIVEL_1 = 5;
    private static final int NIVEL_2 = 10;
    private static final int NIVEL_3 = 20;
    private static final int ALERTA_ATAQUE = 30;

    private static final int BLOQUEIO_NIVEL_1_MIN = 15;
    private static final int BLOQUEIO_NIVEL_2_MIN = 60;
    private static final int BLOQUEIO_NIVEL_3_MIN = 240;

    /** Teto de IPs rastreados. Impede que o mapa vire vetor de exaustão de memória. */
    static final int MAX_ENTRADAS = 50_000;

    /** A cada N registros de falha, roda uma varredura de expirados. */
    private static final int INTERVALO_LIMPEZA = 500;

    private final Map<String, IpAttempt> tentativas = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger escritasDesdeLimpeza =
            new java.util.concurrent.atomic.AtomicInteger();
    private final ErroLogRepository erroLogRepository;
    private final AlertaErroService alertaErroService;

    public GlobalRateLimiter(ErroLogRepository erroLogRepository, AlertaErroService alertaErroService) {
        this.erroLogRepository = erroLogRepository;
        this.alertaErroService = alertaErroService;
    }

    public boolean isBlocked(String ip) {
        IpAttempt attempt = tentativas.get(ip);
        if (attempt == null) return false;

        if (attempt.bloqueadoAte != null && attempt.bloqueadoAte.isBefore(LocalDateTime.now())) {
            // Bloqueio expirou, mas NÃO reseta contagem (acumula pra escalar)
            attempt.bloqueadoAte = null;
            return false;
        }

        return attempt.bloqueadoAte != null;
    }

    public long getMinutosRestantes(String ip) {
        IpAttempt attempt = tentativas.get(ip);
        if (attempt == null || attempt.bloqueadoAte == null) return 0;
        long segundos = Duration.between(LocalDateTime.now(), attempt.bloqueadoAte).getSeconds();
        return Math.max(0, (segundos + 59) / 60);
    }

    public void registrarFalha(String ip, String rota) {
        if (escritasDesdeLimpeza.incrementAndGet() >= INTERVALO_LIMPEZA) {
            escritasDesdeLimpeza.set(0);
            limparExpirados();
        }

        tentativas.compute(ip, (key, attempt) -> {
            if (attempt == null) {
                attempt = new IpAttempt();
            }

            // Reset total se ficou 24h sem tentativa
            if (attempt.ultimaTentativa.plusHours(24).isBefore(LocalDateTime.now())) {
                attempt = new IpAttempt();
            }

            attempt.contagem++;
            attempt.ultimaTentativa = LocalDateTime.now();

            // Escalar bloqueio
            if (attempt.contagem >= NIVEL_3 && attempt.nivel < 3) {
                attempt.bloqueadoAte = LocalDateTime.now().plusMinutes(BLOQUEIO_NIVEL_3_MIN);
                attempt.nivel = 3;
                logger.warn("[RATE LIMIT N3] IP {} bloqueado por {}h após {} tentativas em {}",
                        ip, BLOQUEIO_NIVEL_3_MIN / 60, attempt.contagem, rota);
            } else if (attempt.contagem >= NIVEL_2 && attempt.nivel < 2) {
                attempt.bloqueadoAte = LocalDateTime.now().plusMinutes(BLOQUEIO_NIVEL_2_MIN);
                attempt.nivel = 2;
                logger.warn("[RATE LIMIT N2] IP {} bloqueado por 1h após {} tentativas em {}",
                        ip, attempt.contagem, rota);
            } else if (attempt.contagem >= NIVEL_1 && attempt.nivel < 1) {
                attempt.bloqueadoAte = LocalDateTime.now().plusMinutes(BLOQUEIO_NIVEL_1_MIN);
                attempt.nivel = 1;
                logger.warn("[RATE LIMIT N1] IP {} bloqueado por 15min após {} tentativas em {}",
                        ip, attempt.contagem, rota);
            }

            // Alerta de ataque
            if (attempt.contagem == ALERTA_ATAQUE && !attempt.alertaEnviado) {
                attempt.alertaEnviado = true;
                alertarPossivelAtaque(ip, rota, attempt.contagem);
            }

            return attempt;
        });
    }

    public void registrarSucesso(String ip) {
        tentativas.remove(ip);
    }

    public int getTentativasRestantes(String ip) {
        IpAttempt attempt = tentativas.get(ip);
        if (attempt == null) return NIVEL_1;
        return Math.max(0, NIVEL_1 - attempt.contagem);
    }

    /**
     * Descarta entradas ociosas há mais de 24h (que já seriam zeradas na próxima
     * tentativa) e, se ainda assim o mapa estourar o teto, remove as mais antigas.
     * Entradas com bloqueio ativo nunca são descartadas — descartá-las liberaria
     * justamente quem está bloqueado.
     */
    public void limparExpirados() {
        LocalDateTime agora = LocalDateTime.now();
        tentativas.entrySet().removeIf(e -> {
            IpAttempt a = e.getValue();
            boolean bloqueado = a.bloqueadoAte != null && a.bloqueadoAte.isAfter(agora);
            return !bloqueado && a.ultimaTentativa.plusHours(24).isBefore(agora);
        });

        int excedente = tentativas.size() - MAX_ENTRADAS;
        if (excedente <= 0) {
            return;
        }

        logger.warn("[RATE LIMIT] {} IPs rastreados excedem o teto de {} — descartando os mais antigos",
                tentativas.size(), MAX_ENTRADAS);
        tentativas.entrySet().stream()
                .filter(e -> e.getValue().bloqueadoAte == null
                        || e.getValue().bloqueadoAte.isBefore(agora))
                .sorted(java.util.Comparator.comparing(e -> e.getValue().ultimaTentativa))
                .limit(excedente)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(tentativas::remove);
    }

    /** Exposto para teste — número de IPs atualmente rastreados. */
    public int tamanho() {
        return tentativas.size();
    }

    private void alertarPossivelAtaque(String ip, String rota, int tentativas) {
        logger.error("[POSSÍVEL ATAQUE] IP {} com {} tentativas em {}", ip, tentativas, rota);

        try {
            ErroLog erro = new ErroLog();
            erro.setTipo("POSSIVEL_ATAQUE_BRUTE_FORCE");
            erro.setMensagem("IP " + ip + " atingiu " + tentativas + " tentativas em " + rota);
            erro.setStacktrace("Rate limiter detectou excesso de tentativas.\nIP: " + ip + "\nRota: " + rota + "\nTentativas: " + tentativas);
            erro.setUrl(rota);
            erro.setMetodoHttp("POST");
            erro.setIp(ip);
            erroLogRepository.save(erro);

            alertaErroService.alertarExcecao(erro);
            erro.setAlertaEnviado(true);
            erroLogRepository.save(erro);
        } catch (Exception e) {
            logger.error("[ALERTA ATAQUE FALHOU] {}", e.getMessage());
        }
    }

    private static class IpAttempt {
        int contagem = 0;
        int nivel = 0;
        boolean alertaEnviado = false;
        LocalDateTime ultimaTentativa = LocalDateTime.now();
        LocalDateTime bloqueadoAte = null;
    }
}
