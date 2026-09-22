package com.sih.dataservice.ml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.complaints.entity.*;
import com.sih.dataservice.complaints.repository.CaseEventRepository;
import com.sih.dataservice.complaints.repository.ComplaintAccountRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.gate.model.GateEvaluationResult;
import com.sih.dataservice.gate.service.AnomalyGateService;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.model.GraphNode;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.ml.client.ModelClient;
import com.sih.dataservice.ml.dto.PredictionRequest;
import com.sih.dataservice.ml.dto.PredictionResponse;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.entity.Prediction;
import com.sih.dataservice.ml.repository.ModelVersionRepository;
import com.sih.dataservice.ml.repository.PredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private ComplaintAccountRepository complaintAccountRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private ModelVersionRepository modelVersionRepository;

    @Mock
    private CaseEventRepository caseEventRepository;

    @Mock
    private TemporalGraphEngine graphEngine;

    @Mock
    private AnomalyGateService anomalyGateService;

    @Mock
    private ModelClient modelClient;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneId.of("UTC"));

    private PredictionService predictionService;

    private UUID complaintId;
    private Complaint complaint;
    private FinancialEntity entity;
    private ComplaintAccount account;

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(
                complaintRepository,
                complaintAccountRepository,
                predictionRepository,
                modelVersionRepository,
                caseEventRepository,
                graphEngine,
                anomalyGateService,
                modelClient,
                auditService,
                objectMapper,
                clock
        );

        complaintId = UUID.randomUUID();
        complaint = new Complaint();
        complaint.setId(complaintId);
        complaint.setStatus(ComplaintStatus.FILED);
        complaint.setAmount(BigDecimal.valueOf(50000));
        complaint.setIncidentTime(Instant.parse("2026-09-22T09:00:00Z"));

        entity = new FinancialEntity("hash123", "enc123", null, EntityType.ACCOUNT);
        entity.setId(UUID.randomUUID());
        account = new ComplaintAccount(complaint, entity, AccountRole.SUSPECT);
    }

    @Test
    @DisplayName("scoreComplaint succeeds with ML model response and auto-triages FILED case")
    void testScoreComplaint_Success() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintAccountRepository.findByComplaintId(complaintId)).thenReturn(List.of(account));

        Map<UUID, GraphNode> nodes = new HashMap<>();
        GraphNode gNode = new GraphNode(entity.getId(), "hash123", null, EntityType.ACCOUNT, Instant.now());
        gNode.setRiskScore(0.8);
        nodes.put(entity.getId(), gNode);
        Subgraph subgraph = new Subgraph(entity.getId(), Map.of(entity.getId(), 0), nodes, List.of());
        when(graphEngine.extractSubgraph(eq(entity.getId()), eq(2), isNull(), isNull(), eq(50)))
                .thenReturn(subgraph);

        PredictionResponse response = new PredictionResponse(0.875, 0.92, List.of(), List.of(), "graphsage-v1.0.0");
        when(modelClient.predict(any(PredictionRequest.class))).thenReturn(response);

        ModelVersion mv = new ModelVersion("graphsage", "v1.0.0", "{}", ModelVersionStatus.ACTIVE);
        when(modelVersionRepository.findByNameAndStatus("graphsage", ModelVersionStatus.ACTIVE))
                .thenReturn(Optional.of(mv));

        when(predictionRepository.save(any(Prediction.class))).thenAnswer(inv -> inv.getArgument(0));

        Prediction result = predictionService.scoreComplaint(complaintId);

        assertThat(result).isNotNull();
        assertThat(result.isModelAvailable()).isTrue();
        assertThat(result.getRisk()).isEqualByComparingTo(BigDecimal.valueOf(0.8750));
        assertThat(result.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.9200));
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.TRIAGED);

        verify(caseEventRepository).save(any(CaseEvent.class));
        verify(auditService).log(isNull(), eq("SYSTEM"), eq("SCORE_COMPLAINT"), eq("COMPLAINT"), eq(complaintId.toString()), anyString(), anyString());
    }

    @Test
    @DisplayName("scoreComplaint falls back to Anomaly Gate when model service fails (FR-PRD-3)")
    void testScoreComplaint_FallbackOnModelFailure() {
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.of(complaint));
        when(complaintAccountRepository.findByComplaintId(complaintId)).thenReturn(List.of(account));

        when(graphEngine.extractSubgraph(any(), anyInt(), any(), any(), anyInt()))
                .thenReturn(new Subgraph(entity.getId(), Map.of(), Map.of(), List.of()));

        when(modelClient.predict(any())).thenThrow(new RuntimeException("Connection timeout"));

        GateEvaluationResult gateResult = new GateEvaluationResult(entity.getId(), true, false, 4.5, 10, "Velocity spike");
        when(anomalyGateService.evaluate(eq(entity.getId()), anyDouble(), any(Instant.class)))
                .thenReturn(gateResult);

        ModelVersion mv = new ModelVersion("graphsage", "v1.0.0", "{}", ModelVersionStatus.ACTIVE);
        when(modelVersionRepository.findByNameAndStatus("graphsage", ModelVersionStatus.ACTIVE))
                .thenReturn(Optional.of(mv));

        when(predictionRepository.save(any(Prediction.class))).thenAnswer(inv -> inv.getArgument(0));

        Prediction result = predictionService.scoreComplaint(complaintId);

        assertThat(result).isNotNull();
        assertThat(result.isModelAvailable()).isFalse();
        assertThat(result.getRisk()).isEqualByComparingTo(BigDecimal.valueOf(0.7500));
        assertThat(result.getExplanation()).contains("Model service unavailable");
        assertThat(result.getExplanation()).contains("\"gateTripped\":true");
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.TRIAGED);
    }
}
