package com.delibera.service;

import com.delibera.config.CotaPublica;
import com.delibera.model.dto.SuporteContatoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SuporteService {

    private static final Logger logger = LoggerFactory.getLogger(SuporteService.class);
    private static final int LIMITE_SUPORTE_HORA = 5;

    private final CotaPublica cotaPublica;
    private final EmailHtmlService emailHtmlService;

    public SuporteService(CotaPublica cotaPublica, EmailHtmlService emailHtmlService) {
        this.cotaPublica = cotaPublica;
        this.emailHtmlService = emailHtmlService;
    }

    public void enviar(SuporteContatoDTO contato, String ip) {
        // A cota é avaliada antes de enfileirar email ou alterar qualquer outro estado.
        if (!cotaPublica.permitir(ip, "/suporte", LIMITE_SUPORTE_HORA)) {
            throw new CotaExcedidaException();
        }
        emailHtmlService.enviarContatoSuporte(contato.getNome().trim(), contato.getEmail().trim(),
                contato.getAssunto().trim(), contato.getMensagem().trim());
        logger.info("[SUPORTE] Mensagem de {} ({}) sobre '{}' enfileirada",
                contato.getNome(), contato.getEmail(), contato.getAssunto());
    }

    public static class CotaExcedidaException extends RuntimeException {
    }
}
