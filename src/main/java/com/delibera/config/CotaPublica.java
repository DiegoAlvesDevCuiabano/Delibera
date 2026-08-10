package com.delibera.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cota de ações públicas bem-sucedidas, por IP e por hora.
 *
 * <p>Distinta do {@link GlobalRateLimiter}, que só conta falhas. Sem uma cota sobre o
 * sucesso, um anônimo podia criar solicitações e mandar mensagens de suporte
 * indefinidamente — cada uma disparando um email pelo SMTP do produto (queimando a
 * reputação do remetente) e, no caso de solicitação, gravando um anexo de até 10MB.
 *
 * <p>Os limites são generosos de propósito: numa faculdade inteira atrás de um NAT,
 * vários alunos legítimos compartilham o mesmo IP.
 */
@Component
public class CotaPublica {

    private static final Logger logger = LoggerFactory.getLogger(CotaPublica.class);

    private static final int JANELA_MINUTOS = 60;
    private static final int MAX_ENTRADAS = 50_000;

    private final Map<String, Deque<LocalDateTime>> usos = new ConcurrentHashMap<>();

    /**
     * Consome uma unidade da cota.
     *
     * @return true se a ação está dentro do limite; false se estourou.
     */
    public boolean permitir(String ip, String rota, int limitePorHora) {
        String chave = rota + "|" + ip;
        LocalDateTime corte = LocalDateTime.now().minusMinutes(JANELA_MINUTOS);

        if (usos.size() > MAX_ENTRADAS) {
            limpar(corte);
        }

        Deque<LocalDateTime> janela = usos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (janela) {
            while (!janela.isEmpty() && janela.peekFirst().isBefore(corte)) {
                janela.pollFirst();
            }
            if (janela.size() >= limitePorHora) {
                logger.warn("[COTA] IP {} atingiu {} usos/hora em {}", ip, limitePorHora, rota);
                return false;
            }
            janela.addLast(LocalDateTime.now());
            return true;
        }
    }

    private void limpar(LocalDateTime corte) {
        usos.entrySet().removeIf(e -> {
            Deque<LocalDateTime> janela = e.getValue();
            synchronized (janela) {
                return janela.isEmpty() || janela.peekLast().isBefore(corte);
            }
        });
    }

    /** Exposto para teste. */
    public void zerar(String ip, String rota) {
        usos.remove(rota + "|" + ip);
    }
}
