package com.sih.dataservice.ml.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.config.SecurityConfig;
import com.sih.dataservice.ml.dto.ModelVersionDetailDto;
import com.sih.dataservice.ml.dto.RetrainRequestDto;
import com.sih.dataservice.ml.dto.RetrainingDashboardDto;
import com.sih.dataservice.ml.dto.TrainingSnapshotDto;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.entity.TrainingSnapshot;
import com.sih.dataservice.ml.repository.TrainingSnapshotRepository;
import com.sih.dataservice.ml.service.RetrainingService;
import com.sih.dataservice.ml.service.SnapshotExportService;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MlOpsController.class)
@Import(SecurityConfig.class)
class MlOpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SnapshotExportService snapshotExportService;

    @MockBean
    private RetrainingService retrainingService;

    @MockBean
    private TrainingSnapshotRepository snapshotRepository;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    private UserPrincipal cyberOfficerPrincipal;
    private UserPrincipal policePrincipal;

    @BeforeEach
    void setUp() {
        cyberOfficerPrincipal = new UserPrincipal(
                UUID.randomUUID(), "cyber@test.gov", "hash", "Cyber Officer",
                UserRole.CYBER_OFFICER, null, null, 1, UserStatus.ACTIVE
        );

        policePrincipal = new UserPrincipal(
                UUID.randomUUID(), "police@test.gov", "hash", "Inspector Roy",
                UserRole.POLICE, null, "/OD/KHORDHA/", 1, UserStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("GET /ml-ops/dashboard returns dashboard for CYBER_OFFICER")
    void testGetDashboard_CyberOfficer() throws Exception {
        RetrainingDashboardDto dto = new RetrainingDashboardDto(
                50L, 40L, 10L, 1, Instant.now(), List.of(), List.of()
        );
        when(retrainingService.getDashboard()).thenReturn(dto);

        mockMvc.perform(get("/ml-ops/dashboard")
                        .with(user(cyberOfficerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLabeledCases").value(50))
                .andExpect(jsonPath("$.data.casesInLatestSnapshot").value(40))
                .andExpect(jsonPath("$.data.newCasesSinceSnapshot").value(10));
    }

    @Test
    @DisplayName("GET /ml-ops/dashboard is forbidden for POLICE")
    void testGetDashboard_PoliceForbidden() throws Exception {
        mockMvc.perform(get("/ml-ops/dashboard")
                        .with(user(policePrincipal)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /ml-ops/snapshots succeeds for CYBER_OFFICER")
    void testExportSnapshot_Success() throws Exception {
        UUID snapId = UUID.randomUUID();
        TrainingSnapshot snapshot = new TrainingSnapshot();
        snapshot.setId(snapId);
        snapshot.setVersion(1);
        snapshot.setCaseCount(25);
        snapshot.setChecksum("sha256checksum");
        snapshot.setStorageUri("/data/snap.jsonl");

        when(snapshotExportService.exportSnapshot(any(), any())).thenReturn(snapshot);

        mockMvc.perform(post("/ml-ops/snapshots")
                        .with(user(cyberOfficerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(snapId.toString()))
                .andExpect(jsonPath("$.data.caseCount").value(25))
                .andExpect(jsonPath("$.data.checksum").value("sha256checksum"));
    }

    @Test
    @DisplayName("POST /ml-ops/retrain initiates training candidate for CYBER_OFFICER")
    void testRetrainCandidate_Success() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        RetrainRequestDto req = new RetrainRequestDto("graphsage", snapshotId, false);

        ModelVersionDetailDto modelDto = new ModelVersionDetailDto();
        modelDto.setId(modelId);
        modelDto.setName("graphsage");
        modelDto.setVersion("v2.1.0-candidate");
        modelDto.setStatus(ModelVersionStatus.CANDIDATE);
        modelDto.setSnapshotVersion(1);

        when(retrainingService.triggerRetraining(any(), any(), any())).thenReturn(modelDto);

        mockMvc.perform(post("/ml-ops/retrain")
                        .with(user(cyberOfficerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("graphsage"))
                .andExpect(jsonPath("$.data.status").value("CANDIDATE"));
    }

    @Test
    @DisplayName("POST /ml-ops/models/{id}/promote promotes candidate to ACTIVE")
    void testPromoteModel_Success() throws Exception {
        UUID modelId = UUID.randomUUID();
        ModelVersionDetailDto modelDto = new ModelVersionDetailDto();
        modelDto.setId(modelId);
        modelDto.setName("graphsage");
        modelDto.setVersion("v2.1.0");
        modelDto.setStatus(ModelVersionStatus.ACTIVE);

        when(retrainingService.promoteCandidate(eq(modelId), any(), any())).thenReturn(modelDto);

        mockMvc.perform(post("/ml-ops/models/" + modelId + "/promote")
                        .with(user(cyberOfficerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
