package com.delibera.model.entity;

import com.delibera.model.enums.StatusSolicitacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "historico_solicitacao")
@Getter @Setter @NoArgsConstructor
public class HistoricoSolicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StatusSolicitacao statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSolicitacao statusNovo;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;
}
