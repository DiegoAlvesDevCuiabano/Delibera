package com.delibera.repository;

import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    List<Solicitacao> findByAlunoEmailOrderByCriadoEmDesc(String alunoEmail);

    List<Solicitacao> findByInstituicaoIdOrderByCriadoEmDesc(Long instituicaoId);

    List<Solicitacao> findByInstituicaoIdAndStatusOrderByCriadoEmAsc(Long instituicaoId, StatusSolicitacao status);

    Optional<Solicitacao> findByProtocolo(String protocolo);

    long countByInstituicaoId(Long instituicaoId);

    long countByInstituicaoIdAndStatus(Long instituicaoId, StatusSolicitacao status);

    @Query("SELECT s FROM Solicitacao s WHERE s.instituicao.id = :instId AND s.status IN :statuses ORDER BY s.criadoEm ASC")
    List<Solicitacao> findPendentesByInstituicao(@Param("instId") Long instId, @Param("statuses") List<StatusSolicitacao> statuses);
}
