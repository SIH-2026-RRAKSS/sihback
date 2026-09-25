package com.sih.dataservice.freeze.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.config.SecurityConfig;
import com.sih.dataservice.freeze.dto.CreateFreezeRequestDto;
import com.sih.dataservice.freeze.dto.FreezeRequestResponseDto;
import com.sih.dataservice.freeze.dto.RespondFreezeRequestDto;
import com.sih.dataservice.freeze.entity.FreezeRequestStatus;
import com.sih.dataservice.freeze.scheduler.FreezeSlaScheduler;
import com.sih.dataservice.freeze.service.FreezeRequestService;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FreezeRequestController.class)
@Import(SecurityConfig.class)
class FreezeRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FreezeRequestService freezeRequestService;

    @MockBean
    private FreezeSlaScheduler freezeSlaScheduler;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    private UserPrincipal policePrincipal;
    private UserPrincipal bankPrincipal;
    private UUID complaintId;
    private UUID entityId;
    private UUID freezeRequestId;

    @BeforeEach
    void setUp() {
        complaintId = UUID.randomUUID();
        entityId = UUID.randomUUID();
        freezeRequestId = UUID.randomUUID();

        policePrincipal = new UserPrincipal(
                UUID.randomUUID(), "police@test.gov", "hash", "Inspector Roy",
                UserRole.POLICE, null, "/OD/KHORDHA/", 1, UserStatus.ACTIVE
        );

        bankPrincipal = new UserPrincipal(
                UUID.randomUUID(), "bank@test.com", "hash", "Bank Manager",
                UserRole.BANK_EMPLOYEE, UUID.randomUUID(), null, 1, UserStatus.ACTIVE
        );
    }

    @Test
    void createFreezeRequest_asPolice_returnsCreated() throws Exception {
        CreateFreezeRequestDto request = new CreateFreezeRequestDto(complaintId, entityId, "Freeze target mule account");

        FreezeRequestResponseDto responseDto = new FreezeRequestResponseDto();
        responseDto.setId(freezeRequestId);
        responseDto.setComplaintId(complaintId);
        responseDto.setEntityId(entityId);
        responseDto.setStatus(FreezeRequestStatus.OPEN);
        responseDto.setDueAt(Instant.now().plusSeconds(7 * 86400));

        when(freezeRequestService.createFreezeRequest(any(), any(), any())).thenReturn(responseDto);

        mockMvc.perform(post("/freeze-requests")
                        .with(user(policePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(freezeRequestId.toString()))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void createFreezeRequest_asBankEmployee_returnsForbidden() throws Exception {
        CreateFreezeRequestDto request = new CreateFreezeRequestDto(complaintId, entityId, "Freeze target");

        mockMvc.perform(post("/freeze-requests")
                        .with(user(bankPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void respondFreezeRequest_asBankEmployee_returnsOk() throws Exception {
        RespondFreezeRequestDto request = new RespondFreezeRequestDto(FreezeRequestStatus.FROZEN, "Account balance frozen");

        FreezeRequestResponseDto responseDto = new FreezeRequestResponseDto();
        responseDto.setId(freezeRequestId);
        responseDto.setStatus(FreezeRequestStatus.FROZEN);
        responseDto.setResponseNote("Account balance frozen");

        when(freezeRequestService.respondFreezeRequest(eq(freezeRequestId), any(), any(), any())).thenReturn(responseDto);

        mockMvc.perform(post("/freeze-requests/" + freezeRequestId + "/respond")
                        .with(user(bankPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("FROZEN"));
    }

    @Test
    void respondFreezeRequest_asPolice_returnsForbidden() throws Exception {
        RespondFreezeRequestDto request = new RespondFreezeRequestDto(FreezeRequestStatus.FROZEN, "Police cannot respond");

        mockMvc.perform(post("/freeze-requests/" + freezeRequestId + "/respond")
                        .with(user(policePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listFreezeRequests_asPolice_returnsOk() throws Exception {
        when(freezeRequestService.listFreezeRequests(any(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/freeze-requests")
                        .with(user(policePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void runSlaCheck_asCyberOfficer_returnsOk() throws Exception {
        UserPrincipal cyberPrincipal = new UserPrincipal(
                UUID.randomUUID(), "cyber@test.gov", "hash", "Cyber Officer",
                UserRole.CYBER_OFFICER, null, null, 1, UserStatus.ACTIVE
        );

        when(freezeSlaScheduler.runSlaCheck()).thenReturn(Map.of("remindersSent", 2, "escalationsTriggered", 1));

        mockMvc.perform(post("/freeze-requests/run-sla-check")
                        .with(user(cyberPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindersSent").value(2))
                .andExpect(jsonPath("$.data.escalationsTriggered").value(1));
    }
}
