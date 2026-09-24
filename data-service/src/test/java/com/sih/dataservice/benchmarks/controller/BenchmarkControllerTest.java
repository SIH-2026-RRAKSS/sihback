package com.sih.dataservice.benchmarks.controller;

import com.sih.dataservice.auth.jwt.JwtTokenService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.benchmarks.dto.ModelBenchmarkMetrics;
import com.sih.dataservice.benchmarks.dto.StreamingBenchmarkResult;
import com.sih.dataservice.benchmarks.dto.ThreeWayBenchmarkResult;
import com.sih.dataservice.benchmarks.service.BenchmarkService;
import com.sih.dataservice.common.config.SecurityConfig;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BenchmarkController.class)
@Import(SecurityConfig.class)
class BenchmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BenchmarkService benchmarkService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private UserRepository userRepository;

    private UserPrincipal cyberOfficerPrincipal;
    private UserPrincipal complainantPrincipal;

    @BeforeEach
    void setUp() {
        cyberOfficerPrincipal = new UserPrincipal(
                UUID.randomUUID(), "cyber@test.gov", "hash", "Cyber Officer",
                UserRole.CYBER_OFFICER, null, null, 1, UserStatus.ACTIVE
        );

        complainantPrincipal = new UserPrincipal(
                UUID.randomUUID(), "citizen@test.com", "hash", "Citizen",
                UserRole.COMPLAINANT, null, null, 1, UserStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("GET /benchmarks/three-way returns 3-way evaluation results")
    void testGetThreeWayBenchmark_Success() throws Exception {
        ModelBenchmarkMetrics gate = new ModelBenchmarkMetrics("anomaly-gate-heuristic", 0.75, 0.80, 0.77, 0.85, 0.78, 12000.0, 0.08, 100);
        ModelBenchmarkMetrics xgb = new ModelBenchmarkMetrics("xgboost-v1.0.0", 0.89, 0.85, 0.87, 0.92, 0.88, 4500.0, 0.22, 100);
        ModelBenchmarkMetrics sage = new ModelBenchmarkMetrics("graphsage-v1.0.0", 0.94, 0.92, 0.93, 0.96, 0.94, 1800.0, 0.55, 100);

        ThreeWayBenchmarkResult res = new ThreeWayBenchmarkResult(
                "synthetic_benchmark_seed42", 100, 20, gate, xgb, sage, 78.5, Instant.now()
        );
        when(benchmarkService.runThreeWayBenchmark(anyString())).thenReturn(res);

        mockMvc.perform(get("/benchmarks/three-way")
                        .with(user(cyberOfficerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasetName").value("synthetic_benchmark_seed42"))
                .andExpect(jsonPath("$.data.gateOnly.modelName").value("anomaly-gate-heuristic"))
                .andExpect(jsonPath("$.data.xgboost.modelName").value("xgboost-v1.0.0"))
                .andExpect(jsonPath("$.data.graphSage.modelName").value("graphsage-v1.0.0"))
                .andExpect(jsonPath("$.data.gateReductionPercentage").value(78.5));
    }

    @Test
    @DisplayName("GET /benchmarks/streaming returns streaming velocity benchmarks")
    void testGetStreamingBenchmark_Success() throws Exception {
        StreamingBenchmarkResult res = new StreamingBenchmarkResult(
                "streaming_velocity_benchmark", 200, 50, 4000.0, 30, 30, 0.85, 0.12, 0.54, Instant.now()
        );
        when(benchmarkService.runStreamingBenchmark(anyInt())).thenReturn(res);

        mockMvc.perform(get("/benchmarks/streaming?count=200")
                        .with(user(cyberOfficerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasetName").value("streaming_velocity_benchmark"))
                .andExpect(jsonPath("$.data.throughputTxPerSec").value(4000.0))
                .andExpect(jsonPath("$.data.stage2ReductionRatio").value(0.85));
    }

    @Test
    @DisplayName("GET /benchmarks/three-way is forbidden for COMPLAINANT")
    void testGetThreeWayBenchmark_ForbiddenForComplainant() throws Exception {
        mockMvc.perform(get("/benchmarks/three-way")
                        .with(user(complainantPrincipal)))
                .andExpect(status().isForbidden());
    }
}
