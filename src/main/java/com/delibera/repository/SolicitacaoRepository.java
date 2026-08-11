package com.delibera.repository;

import com.delibera.model.entity.Solicitacao;
import com.delibera.model.enums.StatusSolicitacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    @Query("SELECT s FROM Solicitacao s WHERE LOWER(s.alunoEmail) = LOWER(:email) ORDER BY s.criadoEm DESC")
    List<Solicitacao> findByAlunoEmailOrderByCriadoEmDesc(@Param("email") String alunoEmail);

    List<Solicitacao> findByInstituicaoIdOrderByCriadoEmDesc(Long instituicaoId);

    List<Solicitacao> findByInstituicaoIdAndStatusOrderByCriadoEmAsc(Long instituicaoId, StatusSolicitacao status);

    Optional<Solicitacao> findByProtocolo(String protocolo);

    Optional<Solicitacao> findByIdAndInstituicaoId(Long id, Long instituicaoId);

    @Query("SELECT s FROM Solicitacao s WHERE s.protocolo = :protocolo AND LOWER(s.alunoEmail) = LOWER(:email)")
    Optional<Solicitacao> findByProtocoloAndEmail(@Param("protocolo") String protocolo, @Param("email") String email);

    long countByInstituicaoId(Long instituicaoId);

    long countByInstituicaoIdAndStatus(Long instituicaoId, StatusSolicitacao status);

    @Query("SELECT s FROM Solicitacao s WHERE s.instituicao.id = :instId AND s.status IN :statuses ORDER BY s.criadoEm ASC")
    List<Solicitacao> findPendentesByInstituicao(@Param("instId") Long instId, @Param("statuses") List<StatusSolicitacao> statuses);

    @Query("SELECT s FROM Solicitacao s WHERE s.instituicao.id = :instId AND (s.status = 'RESOLVIDO' OR s.status = 'NEGADO')")
    List<Solicitacao> findFinalizadasPorInstituicao(@Param("instId") Long instId);

    Page<Solicitacao> findByInstituicaoIdOrderByCriadoEmDesc(Long instituicaoId, Pageable pageable);

    Page<Solicitacao> findByInstituicaoIdAndStatusOrderByCriadoEmDesc(
            Long instituicaoId, StatusSolicitacao status, Pageable pageable);

    @Query("SELECT s FROM Solicitacao s WHERE s.instituicao.id = :instId " +
           "AND (LOWER(s.alunoNome) LIKE CONCAT('%', LOWER(:busca), '%') " +
           "OR LOWER(s.protocolo) LIKE CONCAT('%', LOWER(:busca), '%')) " +
           "ORDER BY s.criadoEm DESC")
    Page<Solicitacao> findByInstituicaoAndBusca(
            @Param("instId") Long instId,
            @Param("busca") String busca,
            Pageable pageable);

    @Query("SELECT s FROM Solicitacao s WHERE s.instituicao.id = :instId " +
           "AND (LOWER(s.alunoNome) LIKE CONCAT('%', LOWER(:busca), '%') " +
           "OR LOWER(s.protocolo) LIKE CONCAT('%', LOWER(:busca), '%')) " +
           "AND s.status = :status ORDER BY s.criadoEm DESC")
    Page<Solicitacao> findByInstituicaoBuscaAndStatus(
            @Param("instId") Long instId,
            @Param("busca") String busca,
            @Param("status") StatusSolicitacao status,
            Pageable pageable);

    @Query("SELECT s FROM Solicitacao s WHERE s.excluidoEm IS NOT NULL AND s.excluidoEm < :limite AND s.anonimizado = false")
    List<Solicitacao> findParaAnonimizar(@Param("limite") LocalDateTime limite);

    @Query("SELECT s FROM Solicitacao s WHERE s.criadoEm < :limite AND s.anonimizado = false AND s.status IN ('RESOLVIDO', 'NEGADO', 'CANCELADA')")
    List<Solicitacao> findAntigasParaAnonimizar(@Param("limite") LocalDateTime limite);
}
