package com.sih.dataservice.ml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.ml.dto.ModelVersionDetailDto;
import com.sih.dataservice.ml.dto.RetrainRequestDto;
import com.sih.dataservice.ml.dto.RetrainingDashboardDto;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.entity.TrainingSnapshot;
import com.sih.dataservice.ml.repository.ModelVersionRepository;
import com.sih.dataservice.ml.repository.TrainingSnapshotRepository;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrainingServiceTest {

    @Mock
    private ModelVersionRepository modelVersionRepository;

    @Mock
    private TrainingSnapshotRepository snapshotRepository;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper;
    private RetrainingService service;
    private UserPrincipal cyberOfficerPrincipal;
    private User cyberOfficerUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RetrainingService(
                modelVersionRepository,
                snapshotRepository,
                complaintRepository,
                userRepository,
                auditService,
                objectMapper,
                "http://localhost:8001"
        );

        UUID officerId = UUID.randomUUID();
        cyberOfficerPrincipal = new UserPrincipal(
                officerId, "officer@test.gov", "hash", "Cyber Officer",
                UserRole.CYBER_OFFICER, null, null, 1, UserStatus.ACTIVE
        );

        cyberOfficerUser = new User();
        cyberOfficerUser.setId(officerId);
        cyberOfficerUser.setName("Cyber Officer");
        cyberOfficerUser.setRole(UserRole.CYBER_OFFICER);
    }

    @Test
    @DisplayName("triggerRetraining generates candidate model and links to snapshot")
    void testTriggerRetraining_Success() {
        UUID snapshotId = UUID.randomUUID();
        TrainingSnapshot snapshot = new TrainingSnapshot();
        snapshot.setId(snapshotId);
        snapshot.setVersion(1);
        snapshot.setCaseCount(100);
        snapshot.setStorageUri("/tmp/snap-1/graphs.jsonl");

        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(modelVersionRepository.findByName("graphsage")).thenReturn(List.of());
        when(modelVersionRepository.save(any(ModelVersion.class))).thenAnswer(inv -> {
            ModelVersion mv = inv.getArgument(0);
            mv.setId(UUID.randomUUID());
            return mv;
        });

        RetrainRequestDto req = new RetrainRequestDto("graphsage", snapshotId, false);
        ModelVersionDetailDto result = service.triggerRetraining(req, cyberOfficerPrincipal, "127.0.0.1");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("graphsage");
        assertThat(result.getVersion()).contains("candidate");
        assertThat(result.getStatus()).isEqualTo(ModelVersionStatus.CANDIDATE);
        assertThat(result.getSnapshotVersion()).isEqualTo(1);

        verify(auditService).log(eq(cyberOfficerPrincipal.getId()), eq("CYBER_OFFICER"), eq("RETRAIN_MODEL"),
                eq("MODEL_VERSION"), anyString(), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("promoteCandidate activates candidate and retires currently active model")
    void testPromoteCandidate_Success() {
        UUID candidateId = UUID.randomUUID();
        ModelVersion candidate = new ModelVersion("graphsage", "v2.0.0-candidate", "{\"f1\":0.93}", ModelVersionStatus.CANDIDATE);
        candidate.setId(candidateId);

        UUID activeId = UUID.randomUUID();
        ModelVersion currentActive = new ModelVersion("graphsage", "v1.0.0", "{\"f1\":0.88}", ModelVersionStatus.ACTIVE);
        currentActive.setId(activeId);

        when(modelVersionRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(userRepository.findById(cyberOfficerPrincipal.getId())).thenReturn(Optional.of(cyberOfficerUser));
        when(modelVersionRepository.findByNameAndStatus("graphsage", ModelVersionStatus.ACTIVE))
                .thenReturn(Optional.of(currentActive));
        when(modelVersionRepository.save(any(ModelVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ModelVersionDetailDto result = service.promoteCandidate(candidateId, cyberOfficerPrincipal, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(ModelVersionStatus.ACTIVE);
        assertThat(currentActive.getStatus()).isEqualTo(ModelVersionStatus.RETIRED);

        verify(auditService).log(eq(cyberOfficerPrincipal.getId()), eq("CYBER_OFFICER"), eq("PROMOTE_MODEL"),
                eq("MODEL_VERSION"), eq(candidateId.toString()), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("getDashboard aggregates snapshots, active model, and new labeled cases")
    void testGetDashboard() {
        TrainingSnapshot latestSnapshot = new TrainingSnapshot();
        latestSnapshot.setId(UUID.randomUUID());
        latestSnapshot.setVersion(2);
        latestSnapshot.setCaseCount(50);
        latestSnapshot.setCreatedAt(Instant.parse("2026-09-20T00:00:00Z"));

        when(snapshotRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(latestSnapshot));
        when(complaintRepository.countByStatusIn(any())).thenReturn(85L);

        ModelVersion activeModel = new ModelVersion("graphsage", "v1.0.0", "{}", ModelVersionStatus.ACTIVE);
        when(modelVersionRepository.findByStatus(ModelVersionStatus.ACTIVE)).thenReturn(List.of(activeModel));
        when(modelVersionRepository.findByStatus(ModelVersionStatus.CANDIDATE)).thenReturn(List.of());

        RetrainingDashboardDto dashboard = service.getDashboard();

        assertThat(dashboard.getTotalLabeledCases()).isEqualTo(85L);
        assertThat(dashboard.getCasesInLatestSnapshot()).isEqualTo(50L);
        assertThat(dashboard.getNewCasesSinceSnapshot()).isEqualTo(35L);
        assertThat(dashboard.getLatestSnapshotVersion()).isEqualTo(2);
        assertThat(dashboard.getActiveModels()).hasSize(1);
        assertThat(dashboard.getActiveModels().get(0).getVersion()).isEqualTo("v1.0.0");
    }
}
