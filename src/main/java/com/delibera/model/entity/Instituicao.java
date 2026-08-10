package com.delibera.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "instituicao")
@Getter @Setter @NoArgsConstructor
public class Instituicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 20)
    private String sigla;

    @Column(length = 200)
    private String dominio;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    // ── Piloto e Voucher ──
    @Column(name = "piloto_expira_em")
    private LocalDateTime pilotoExpiraEm;

    @Column(name = "voucher_ativado_em")
    private LocalDateTime voucherAtivadoEm;

    @Column(name = "email_piloto_enviado")
    private boolean emailPilotoEnviado = false;

    @Column(name = "email_voucher_enviado")
    private boolean emailVoucherEnviado = false;

    @Column(name = "feedback_respondido")
    private boolean feedbackRespondido = false;

    /**
     * Verifica se a instituição tem acesso ativo (piloto ou voucher).
     * Se pilotoExpiraEm for null, considera acesso liberado (legado/admin).
     */
    public boolean isAcessoAtivo() {
        if (pilotoExpiraEm == null) return true;
        LocalDateTime agora = LocalDateTime.now();
        // Piloto ainda ativo
        if (agora.isBefore(pilotoExpiraEm)) return true;
        // Voucher ativo
        if (voucherAtivadoEm != null) {
            return agora.isBefore(voucherAtivadoEm.plusDays(30));
        }
        return false;
    }

    /** Ativa o piloto de 30 dias a partir de agora */
    public void ativarPiloto() {
        this.pilotoExpiraEm = LocalDateTime.now().plusDays(30);
    }

    /** Ativa o voucher de extensão (+30 dias) */
    public void ativarVoucher() {
        this.voucherAtivadoEm = LocalDateTime.now();
    }

    public Instituicao(String nome, String sigla) {
        this.nome = nome;
        this.sigla = sigla;
        ativarPiloto();
    }
}
