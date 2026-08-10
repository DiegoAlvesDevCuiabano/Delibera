package com.delibera.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de exceções não tratadas capturadas pelo sistema.
 * Serve como monitoramento interno — visível na tela admin.
 */
@Entity
@Table(name = "erro_log")
@Getter @Setter @NoArgsConstructor
public class ErroLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tipo da exceção (ex: NullPointerException) */
    @Column(nullable = false, length = 300)
    private String tipo;

    /** Mensagem da exceção */
    @Column(columnDefinition = "TEXT")
    private String mensagem;

    /** Stacktrace completo */
    @Column(columnDefinition = "TEXT")
    private String stacktrace;

    /** URL que causou o erro (se disponível) */
    @Column(length = 500)
    private String url;

    /** Método HTTP (GET, POST, etc) */
    @Column(length = 10)
    private String metodoHttp;

    /** Usuário logado (email) — null se anônimo */
    @Column(length = 200)
    private String usuario;

    /** IP do cliente */
    @Column(length = 50)
    private String ip;

    /** Se o alerta por email foi enviado */
    @Column(nullable = false)
    private boolean alertaEnviado = false;

    /** Se o erro foi resolvido/visto pelo admin */
    @Column(nullable = false)
    private boolean resolvido = false;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
    }
}
