package com.sih.dataservice.complaints.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.config.SecurityConfig;
import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.complaints.dto.UpdateCaseLabelRequest;
import com.sih.dataservice.complaints.entity.CaseLabel;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.service.CaseWorkflowService;
import com.sih.dataservice.complaints.service.ComplaintService;
import com.sih.dataservice.complaints.service.EvidenceStorageService;
import com.sih.dataservice.complaints.service.ReferenceNumberService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import(SecurityConfig.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ComplaintService complaintService;

    @MockBean
    private CaseWorkflowService caseWorkflowService;

    @MockBean
    private com.sih.dataservice.graph.service.GraphService graphService;

    @MockBean
    private com.sih.dataservice.ml.service.PredictionService predictionService;

    @MockBean
    private com.sih.dataservice.dossier.service.DossierService dossierService;

    @MockBean
    private com.sih.dataservice.freeze.service.FreezeRequestService freezeRequestService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    private UserPrincipal cyberOfficerPrincipal;
    private UserPrincipal policePrincipal;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        incidentId = UUID.randomUUID();

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
    @DisplayName("PUT /incidents/{id}/label allows CYBER_OFFICER to update label")
    void testUpdateCaseLabel_CyberOfficer() throws Exception {
        UpdateCaseLabelRequest request = new UpdateCaseLabelRequest(CaseLabel.NOT_FRAUD, "Disproved");

        IncidentDetailDto dto = new IncidentDetailDto();
        dto.setId(incidentId);
        dto.setLabel(CaseLabel.NOT_FRAUD);
        dto.setStatus(ComplaintStatus.CLOSED_NOT_FRAUD);

        when(caseWorkflowService.updateCaseLabel(eq(incidentId), eq(CaseLabel.NOT_FRAUD), eq("Disproved"), any(), any()))
                .thenReturn(dto);

        mockMvc.perform(put("/incidents/" + incidentId + "/label")
                        .with(user(cyberOfficerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("NOT_FRAUD"))
                .andExpect(jsonPath("$.data.status").value("CLOSED_NOT_FRAUD"));
    }

    @Test
    @DisplayName("PUT /incidents/{id}/label is forbidden for POLICE")
    void testUpdateCaseLabel_PoliceForbidden() throws Exception {
        UpdateCaseLabelRequest request = new UpdateCaseLabelRequest(CaseLabel.NOT_FRAUD, "Disproved");

        mockMvc.perform(put("/incidents/" + incidentId + "/label")
                        .with(user(policePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
