package com.sih.dataservice.ml.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.entity.*;
import com.sih.dataservice.complaints.repository.ComplaintAccountRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.graph.repository.TransactionRepository;
import com.sih.dataservice.ml.entity.TrainingSnapshot;
import com.sih.dataservice.ml.repository.TrainingSnapshotRepository;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotExportServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintAccountRepository complaintAccountRepository;

    @Mock
    private TrainingSnapshotRepository snapshotRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private SnapshotExportService service;
    private UserPrincipal cyberOfficerPrincipal;
    private User officer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new SnapshotExportService(
                snapshotRepository,
                complaintRepository,
                complaintAccountRepository,
                transactionRepository,
                userRepository,
                auditService,
                objectMapper,
                tempDir.toString()
        );

        UUID officerId = UUID.randomUUID();
        cyberOfficerPrincipal = new UserPrincipal(
                officerId, "cyber@test.gov", "hash", "Cyber Officer",
                UserRole.CYBER_OFFICER, null, null, 1, UserStatus.ACTIVE
        );

        officer = new User();
        officer.setId(officerId);
        officer.setName("Cyber Officer");
        officer.setRole(UserRole.CYBER_OFFICER);
    }

    @Test
    @DisplayName("exportSnapshot generates de-identified snapshot files, manifest, and SHA-256 checksum without PII")
    void testExportSnapshot_Success() throws Exception {
        UUID complaintId = UUID.randomUUID();
        Complaint complaint = new Complaint();
        complaint.setId(complaintId);
        complaint.setStatus(ComplaintStatus.CLOSED_FRAUD);
        complaint.setLabel(CaseLabel.FRAUD);
        complaint.setAmount(BigDecimal.valueOf(75000));
        complaint.setIncidentTime(Instant.parse("2026-09-20T10:00:00Z"));

        UUID entityId = UUID.randomUUID();
        FinancialEntity entity = new FinancialEntity("rawAccHash123", "encRawAccount123", null, EntityType.ACCOUNT);
        entity.setId(entityId);
        ComplaintAccount ca = new ComplaintAccount(complaint, entity, AccountRole.SUSPECT);

        when(userRepository.findById(cyberOfficerPrincipal.getId())).thenReturn(Optional.of(officer));
        when(snapshotRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());
        when(complaintRepository.findByStatusIn(any())).thenReturn(List.of(complaint));
        when(complaintAccountRepository.findByComplaintId(complaintId)).thenReturn(List.of(ca));
        when(transactionRepository.findBySenderIdInOrReceiverIdIn(any())).thenReturn(List.of());

        when(snapshotRepository.save(any(TrainingSnapshot.class))).thenAnswer(inv -> {
            TrainingSnapshot s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        TrainingSnapshot result = service.exportSnapshot(cyberOfficerPrincipal, "127.0.0.1");

        assertThat(result).isNotNull();
        assertThat(result.getCaseCount()).isEqualTo(1);
        assertThat(result.getChecksum()).isNotBlank();
        assertThat(result.getStorageUri()).isNotBlank();

        // Verify exported file exists and contains de-identified data
        Path dataPath = Path.of(result.getStorageUri());
        assertThat(Files.exists(dataPath)).isTrue();
        List<String> lines = Files.readAllLines(dataPath);
        assertThat(lines).hasSize(2);

        // Line 0: Header Manifest
        JsonNode header = objectMapper.readTree(lines.get(0));
        assertThat(header.get("case_count").asInt()).isEqualTo(1);
        assertThat(header.get("fraud_count").asInt()).isEqualTo(1);
        assertThat(header.get("salt_hash").asText()).isNotBlank();

        // Line 1: De-identified Case Graph
        JsonNode record = objectMapper.readTree(lines.get(1));
        assertThat(record.get("label").asInt()).isEqualTo(1);
        assertThat(record.has("complaint_id")).isTrue();
        assertThat(record.has("nodes")).isTrue();
        // Ensure raw hash was HMAC salted and raw account info is absent
        assertThat(record.has("complainant_name")).isFalse();
        assertThat(record.has("phone_number")).isFalse();
        assertThat(record.has("description")).isFalse();

        verify(auditService).log(eq(officer.getId()), eq("CYBER_OFFICER"), eq("CREATE_SNAPSHOT"),
                eq("TRAINING_SNAPSHOT"), anyString(), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("exportSnapshot throws badRequest if no closed labeled cases exist")
    void testExportSnapshot_NoCasesThrows() {
        when(userRepository.findById(cyberOfficerPrincipal.getId())).thenReturn(Optional.of(officer));
        when(snapshotRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());
        when(complaintRepository.findByStatusIn(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.exportSnapshot(cyberOfficerPrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No labeled closed cases available");
    }
}
