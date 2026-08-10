package com.delibera.model.entity;

import com.delibera.model.enums.TipoAcaoAuditoria;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_usuario_timestamp", columnList = "usuario_id, timestamp DESC"),
        @Index(name = "idx_audit_recurso", columnList = "recurso_tipo, recurso_id")
})
@Getter @Setter @NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAcaoAuditoria acao;

    @Column(name = "recurso_tipo", nullable = false, length = 30)
    private String recursoTipo;

    @Column(name = "recurso_id", nullable = false)
    private Long recursoId;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(length = 45)
    private String ip;

    public AuditLog(Usuario usuario, TipoAcaoAuditoria acao, String recursoTipo, Long recursoId, String ip) {
        this.usuario = usuario;
        this.acao = acao;
        this.recursoTipo = recursoTipo;
        this.recursoId = recursoId;
        this.ip = ip;
    }
}
