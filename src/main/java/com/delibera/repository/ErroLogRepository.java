package com.delibera.repository;

import com.delibera.model.entity.ErroLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErroLogRepository extends JpaRepository<ErroLog, Long> {

    Page<ErroLog> findAllByOrderByCriadoEmDesc(Pageable pageable);

    Page<ErroLog> findByResolvidoFalseOrderByCriadoEmDesc(Pageable pageable);

    long countByResolvidoFalse();
}
