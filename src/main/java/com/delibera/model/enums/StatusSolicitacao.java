package com.delibera.model.enums;

public enum StatusSolicitacao {
    EM_ANALISE("Em Análise"),
    AGUARDANDO_PROFESSOR("Aguardando Professor"),
    RESOLVIDO("Resolvido"),
    NEGADO("Negado"),
    CANCELADA("Cancelada");

    private final String descricao;

    StatusSolicitacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
