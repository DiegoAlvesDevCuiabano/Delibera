package com.delibera.repository;

import com.delibera.model.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByInstituicaoIdAndAtivoTrue(Long instituicaoId);

    long countByInstituicaoId(Long instituicaoId);

    boolean existsByEmail(String email);
}
