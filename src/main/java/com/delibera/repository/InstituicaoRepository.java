package com.delibera.repository;

import com.delibera.model.entity.Instituicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface InstituicaoRepository extends JpaRepository<Instituicao, Long> {

    /** Instituições com piloto expirado, sem voucher, email não enviado */
    @Query("SELECT i FROM Instituicao i WHERE i.pilotoExpiraEm < :agora AND i.voucherAtivadoEm IS NULL AND i.emailPilotoEnviado = false AND i.ativo = true")
    List<Instituicao> findPilotoExpirado(LocalDateTime agora);

    /** Instituições com voucher expirado, email não enviado */
    @Query("SELECT i FROM Instituicao i WHERE i.voucherAtivadoEm IS NOT NULL AND i.emailVoucherEnviado = false AND i.ativo = true")
    List<Instituicao> findComVoucher();

    /** Instituicoes no periodo de feedback (piloto ativo, entre dia 12 e 18, feedback nao respondido) */
    @Query("SELECT i FROM Instituicao i WHERE i.pilotoExpiraEm IS NOT NULL AND i.ativo = true AND i.feedbackRespondido = false AND i.emailPilotoEnviado = false")
    List<Instituicao> findAptosFeedback();
}
