package com.delibera.model.entity;

import com.delibera.model.enums.RespostaProfessor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de encaminhamento a professor com código de acesso único.
 * O professor usa protocolo + código para acessar a página pública de resposta.
 * Código expira em 3 dias e é single-use.
 */
@Entity
@Table(name = "parecer_professor")
@Getter @Setter @NoArgsConstructor
public class ParecerProfessor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @Column(nullable = false, length = 200)
    private String emailProfessor;

    /** Código de acesso único gerado pelo sistema (ex: PRF-8K2M4X) */
    @Column(nullable = false, unique = true, length = 20)
    private String codigoAcesso;

    /** Comentário do coordenador ao encaminhar */
    @Column(columnDefinition = "TEXT")
    private String comentarioCoordenador;

    /** Resposta do professor: FEITO, NAO_FEITO, MAIS_INFORMACOES */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RespostaProfessor resposta;

    /** Parecer/comentário do professor */
    @Column(columnDefinition = "TEXT")
    private String parecerProfessor;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    private LocalDateTime respondidoEm;

    @Column(nullable = false)
    private boolean usado = false;

    @PrePersist
    protected void onCreate() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (expiraEm == null) expiraEm = criadoEm.plusDays(3);
    }

    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm);
    }

    public boolean isDisponivel() {
        return !usado && !isExpirado();
    }
}
