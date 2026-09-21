package com.sih.dataservice.audit.repository;

import com.sih.dataservice.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByAction(String action, Pageable pageable);
    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
}
