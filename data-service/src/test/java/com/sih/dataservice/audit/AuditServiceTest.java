package com.sih.dataservice.audit;

import com.sih.dataservice.audit.entity.AuditLog;
import com.sih.dataservice.audit.repository.AuditLogRepository;
import com.sih.dataservice.audit.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    void log_persistsAuditEntryWithCorrectFields() {
        UUID actorId = UUID.randomUUID();
        String role = "CYBER_OFFICER";
        String action = "CASE_CLOSED";
        String entityType = "COMPLAINT";
        String entityId = UUID.randomUUID().toString();
        String detailJson = "{\"label\":\"FRAUD\"}";
        String ipAddress = "127.0.0.1";

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog saved = auditService.log(actorId, role, action, entityType, entityId, detailJson, ipAddress);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog captured = captor.getValue();
        assertThat(captured.getActorId()).isEqualTo(actorId);
        assertThat(captured.getRole()).isEqualTo(role);
        assertThat(captured.getAction()).isEqualTo(action);
        assertThat(captured.getEntityType()).isEqualTo(entityType);
        assertThat(captured.getEntityId()).isEqualTo(entityId);
        assertThat(captured.getDetail()).isEqualTo(detailJson);
        assertThat(captured.getIpAddress()).isEqualTo(ipAddress);
        assertThat(captured.getCreatedAt()).isNotNull();
    }
}
