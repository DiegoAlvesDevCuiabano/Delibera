package com.delibera.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Validação de força de senha.
 * Regras: mínimo 8 caracteres, 1 maiúscula, 1 minúscula, 1 número, 1 especial.
 */
public final class SenhaValidator {

    private SenhaValidator() {}

    public static final int TAMANHO_MINIMO = 8;

    /**
     * Valida a senha e retorna lista de problemas encontrados.
     * Lista vazia = senha válida.
     */
    public static List<String> validar(String senha) {
        List<String> erros = new ArrayList<>();

        if (senha == null || senha.isBlank()) {
            erros.add("Senha é obrigatória");
            return erros;
        }

        if (senha.length() < TAMANHO_MINIMO) {
            erros.add("Mínimo de " + TAMANHO_MINIMO + " caracteres");
        }

        if (!senha.matches(".*[A-Z].*")) {
            erros.add("Pelo menos 1 letra maiúscula");
        }

        if (!senha.matches(".*[a-z].*")) {
            erros.add("Pelo menos 1 letra minúscula");
        }

        if (!senha.matches(".*[0-9].*")) {
            erros.add("Pelo menos 1 número");
        }

        if (!senha.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            erros.add("Pelo menos 1 caractere especial (!@#$%...)");
        }

        return erros;
    }

    /**
     * Retorna true se a senha atende todos os requisitos.
     */
    public static boolean isValida(String senha) {
        return validar(senha).isEmpty();
    }
}
