package com.delibera.model.enums;

public enum RespostaProfessor {
    FEITO("Feito"),
    NAO_FEITO("Não feito"),
    MAIS_INFORMACOES("Preciso de mais informações");

    private final String descricao;

    RespostaProfessor(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
