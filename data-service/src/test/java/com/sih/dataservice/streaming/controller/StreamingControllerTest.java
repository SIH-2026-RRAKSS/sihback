package com.sih.dataservice.streaming.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.config.SecurityConfig;
import com.sih.dataservice.streaming.dto.StartStreamingRequest;
import com.sih.dataservice.streaming.dto.StreamAlertDto;
import com.sih.dataservice.streaming.dto.StreamingStatusDto;
import com.sih.dataservice.streaming.service.StreamingReplayService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreamingController.class)
@Import(SecurityConfig.class)
class StreamingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StreamingReplayService streamingReplayService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    private UserPrincipal cyberOfficerPrincipal;
    private UserPrincipal policePrincipal;
    private UserPrincipal bankPrincipal;

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

        bankPrincipal = new UserPrincipal(
                UUID.randomUUID(), "bank@test.com", "hash", "Bank Employee",
                UserRole.BANK_EMPLOYEE, UUID.randomUUID(), null, 1, UserStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("GET /streaming/events establishes SSE subscription")
    void testSubscribeEvents_Success() throws Exception {
        when(streamingReplayService.registerEmitter()).thenReturn(new SseEmitter());

        mockMvc.perform(get("/streaming/events")
                        .with(user(cyberOfficerPrincipal)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /streaming/start starts replay for CYBER_OFFICER")
    void testStartReplay_Success() throws Exception {
        StartStreamingRequest req = new StartStreamingRequest("synthetic_small", 2.0, 50, false);
        StreamingStatusDto status = new StreamingStatusDto(true, 2.0, 0, 0, 0, 1, "synthetic_small");
        when(streamingReplayService.startReplay(any())).thenReturn(status);

        mockMvc.perform(post("/streaming/start")
                        .with(user(cyberOfficerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running").value(true))
                .andExpect(jsonPath("$.data.speedMultiplier").value(2.0));
    }

    @Test
    @DisplayName("POST /streaming/stop halts replay")
    void testStopReplay_Success() throws Exception {
        StreamingStatusDto status = new StreamingStatusDto(false, 1.0, 10, 2, 2, 1, "synthetic_small");
        when(streamingReplayService.stopReplay()).thenReturn(status);

        mockMvc.perform(post("/streaming/stop")
                        .with(user(cyberOfficerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running").value(false));
    }

    @Test
    @DisplayName("POST /streaming/step executes single transaction through gate and returns result")
    void testStepTransaction_Success() throws Exception {
        StreamAlertDto alert = new StreamAlertDto();
        alert.setUtr("UTR-STEP-1");
        alert.setAmount(BigDecimal.valueOf(100000));
        alert.setGateTripped(true);
        alert.setRiskScore(0.92);

        when(streamingReplayService.step(any())).thenReturn(alert);

        mockMvc.perform(post("/streaming/step?amount=100000.0&utr=UTR-STEP-1")
                        .with(user(policePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.utr").value("UTR-STEP-1"))
                .andExpect(jsonPath("$.data.gateTripped").value(true))
                .andExpect(jsonPath("$.data.riskScore").value(0.92));
    }

    @Test
    @DisplayName("GET /streaming/status returns telemetry")
    void testGetStatus() throws Exception {
        StreamingStatusDto status = new StreamingStatusDto(false, 1.0, 25, 4, 3, 2, "synthetic_small");
        when(streamingReplayService.getStatus()).thenReturn(status);

        mockMvc.perform(get("/streaming/status")
                        .with(user(policePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProcessed").value(25))
                .andExpect(jsonPath("$.data.gateTrippedCount").value(4));
    }

    @Test
    @DisplayName("POST /streaming/start is forbidden for BANK_EMPLOYEE")
    void testStartReplay_BankForbidden() throws Exception {
        mockMvc.perform(post("/streaming/start")
                        .with(user(bankPrincipal)))
                .andExpect(status().isForbidden());
    }
}
