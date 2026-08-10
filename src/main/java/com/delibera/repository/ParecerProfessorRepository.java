package com.delibera.repository;

import com.delibera.model.entity.ParecerProfessor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParecerProfessorRepository extends JpaRepository<ParecerProfessor, Long> {

    Optional<ParecerProfessor> findByCodigoAcesso(String codigoAcesso);

    List<ParecerProfessor> findBySolicitacaoIdOrderByCriadoEmDesc(Long solicitacaoId);
}
