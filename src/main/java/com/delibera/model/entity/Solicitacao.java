package com.delibera.model.entity;

import com.delibera.model.enums.StatusSolicitacao;
import com.delibera.model.enums.TipoSolicitacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "solicitacao")
@Getter @Setter @NoArgsConstructor
public class Solicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String protocolo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoSolicitacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSolicitacao status;

    // ── Dados do aluno (sem login, campos de texto) ──
    @Column(nullable = false, length = 200)
    private String alunoNome;

    @Column(nullable = false, length = 200)
    private String alunoEmail;

    @Column(length = 20)
    private String alunoWhatsapp;

    @Column(length = 200)
    private String alunoCurso;

    @Column(length = 20)
    private String alunoTurma;

    // ── Dados da solicitação ──
    @Column(length = 200)
    private String disciplina;

    @Column(length = 100)
    private String professor;

    private LocalDate dataOcorrencia;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @Column(columnDefinition = "TEXT")
    private String parecer;

    // ── Campos específicos por tipo ──
    @Column(length = 10)
    private String notaAtual;

    @Column(length = 10)
    private String notaEsperada;

    @Column(length = 200)
    private String datasFalta;

    // ── Auditoria ──
    @Column(nullable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    // ── Relacionamentos ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @OneToMany(mappedBy = "solicitacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("criadoEm DESC")
    private List<HistoricoSolicitacao> historico = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    /** Histórico com responsável (coordenador/admin) */
    public void adicionarHistorico(StatusSolicitacao statusAnterior, StatusSolicitacao statusNovo, String observacao, Usuario responsavel) {
        HistoricoSolicitacao h = new HistoricoSolicitacao();
        h.setSolicitacao(this);
        h.setStatusAnterior(statusAnterior);
        h.setStatusNovo(statusNovo);
        h.setObservacao(observacao);
        h.setResponsavel(responsavel);
        h.setCriadoEm(LocalDateTime.now());
        this.historico.add(h);
    }

    /** Histórico sem responsável (ação do aluno — sem login) */
    public void adicionarHistorico(StatusSolicitacao statusAnterior, StatusSolicitacao statusNovo, String observacao) {
        adicionarHistorico(statusAnterior, statusNovo, observacao, null);
    }
}
