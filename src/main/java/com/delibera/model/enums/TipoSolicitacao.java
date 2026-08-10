package com.delibera.model.enums;

public enum TipoSolicitacao {
    ABONO_FALTA("Abono de Falta"),
    SEGUNDA_CHAMADA("Segunda Chamada"),
    AJUSTE_PRESENCA("Ajuste de Presença"),
    CONTESTACAO_NOTA("Contestação de Nota");

    private final String descricao;

    TipoSolicitacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
