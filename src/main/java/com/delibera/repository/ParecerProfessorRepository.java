package com.delibera.repository;

import com.delibera.model.entity.ParecerProfessor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParecerProfessorRepository extends JpaRepository<ParecerProfessor, Long> {

    @EntityGraph(attributePaths = {"solicitacao", "solicitacao.historico"})
    Optional<ParecerProfessor> findByCodigoAcesso(String codigoAcesso);

    @Override
    @EntityGraph(attributePaths = {"solicitacao", "solicitacao.historico"})
    Optional<ParecerProfessor> findById(Long id);

    @Query("SELECT p FROM ParecerProfessor p WHERE p.id = :id")
    Optional<ParecerProfessor> findParaResposta(@Param("id") Long id);

    List<ParecerProfessor> findBySolicitacaoIdOrderByCriadoEmDesc(Long solicitacaoId);
}
