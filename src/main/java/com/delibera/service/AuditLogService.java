package com.delibera.service;

import com.delibera.model.entity.AuditLog;
import com.delibera.model.entity.Usuario;
import com.delibera.model.enums.TipoAcaoAuditoria;
import com.delibera.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void registrar(Usuario usuario, TipoAcaoAuditoria acao, String recursoTipo, Long recursoId, String ip) {
        auditLogRepository.save(new AuditLog(usuario, acao, recursoTipo, recursoId, ip));
    }
}
