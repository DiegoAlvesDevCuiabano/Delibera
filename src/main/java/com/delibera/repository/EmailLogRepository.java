package com.delibera.repository;

import com.delibera.model.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    List<EmailLog> findBySolicitacaoIdOrderByEnviadoEmDesc(Long solicitacaoId);
}
