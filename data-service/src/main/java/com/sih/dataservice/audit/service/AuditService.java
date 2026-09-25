package com.sih.dataservice.audit.service;

import com.sih.dataservice.audit.entity.AuditLog;
import com.sih.dataservice.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog log(UUID actorId, String role, String action, String entityType, String entityId, String detailJson, String ipAddress) {
        log.info("AUDIT: action={}, actorId={}, role={}, entityType={}, entityId={}", 
                action, actorId, role, entityType, entityId);
        
        AuditLog auditLog = new AuditLog(actorId, role, action, entityType, entityId, detailJson, ipAddress);
        return auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsByAction(String action, Pageable pageable) {
        return auditLogRepository.findByAction(action, pageable);
    }
}
