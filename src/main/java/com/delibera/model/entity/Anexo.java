package com.delibera.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "anexo")
@Getter @Setter @NoArgsConstructor
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String nomeOriginal;

    @Column(nullable = false, length = 255)
    private String nomeArmazenado;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long tamanho;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    public Anexo(String nomeOriginal, String nomeArmazenado, String contentType, long tamanho, Solicitacao solicitacao) {
        this.nomeOriginal = nomeOriginal;
        this.nomeArmazenado = nomeArmazenado;
        this.contentType = contentType;
        this.tamanho = tamanho;
        this.solicitacao = solicitacao;
    }

    public String getTamanhoFormatado() {
        if (tamanho < 1024) return tamanho + " B";
        if (tamanho < 1024 * 1024) return String.format("%.1f KB", tamanho / 1024.0);
        return String.format("%.1f MB", tamanho / (1024.0 * 1024));
    }
}
