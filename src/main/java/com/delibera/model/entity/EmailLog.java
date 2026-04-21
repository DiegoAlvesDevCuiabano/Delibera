package com.delibera.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de cada email enviado pelo sistema.
 * Serve como comprovação para a coordenação de que o aluno foi notificado.
 */
@Entity
@Getter
@Setter
@Table(name = "email_log")
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id")
    private Solicitacao solicitacao;

    @Column(nullable = false)
    private String destinatario;

    @Column(nullable = false)
    private String assunto;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String corpo;

    @Column(nullable = false, length = 20)
    private String status; // ENVIADO, FALHOU

    @Column(columnDefinition = "TEXT")
    private String erro;

    @Column(nullable = false)
    private LocalDateTime enviadoEm;

    @PrePersist
    protected void onCreate() {
        if (enviadoEm == null) enviadoEm = LocalDateTime.now();
    }
}
