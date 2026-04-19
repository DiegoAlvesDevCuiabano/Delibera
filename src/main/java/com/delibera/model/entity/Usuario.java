package com.delibera.model.entity;

import com.delibera.model.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter @Setter @NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    // Nullable para ROOT (acesso global, sem tenant)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instituicao_id")
    private Instituicao instituicao;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Usuario(String nome, String email, String senha, Role role, Instituicao instituicao) {
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.role = role;
        this.instituicao = instituicao;
    }

    /** Construtor para ROOT (sem instituição) */
    public Usuario(String nome, String email, String senha, Role role) {
        this(nome, email, senha, role, null);
    }

    public boolean isRoot() {
        return role == Role.ROOT;
    }

    public boolean isGestao() {
        return role == Role.GESTAO;
    }

    public boolean isCrossTenant() {
        return role == Role.ROOT || role == Role.GESTAO;
    }

    public String getIniciais() {
        if (nome == null || nome.isBlank()) return "?";
        String[] partes = nome.trim().split("\\s+");
        if (partes.length == 1) return partes[0].substring(0, 1).toUpperCase();
        return (partes[0].charAt(0) + "" + partes[partes.length - 1].charAt(0)).toUpperCase();
    }
}
