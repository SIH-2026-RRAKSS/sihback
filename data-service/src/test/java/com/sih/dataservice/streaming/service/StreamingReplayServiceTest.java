package com.sih.dataservice.streaming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.complaints.entity.FinancialEntity;
import com.sih.dataservice.datasets.SyntheticDataGenerator;
import com.sih.dataservice.gate.model.GateEvaluationResult;
import com.sih.dataservice.gate.service.AnomalyGateService;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.entity.TransactionSource;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.ml.client.ModelClient;
import com.sih.dataservice.ml.dto.PredictionRequest;
import com.sih.dataservice.ml.dto.PredictionResponse;
import com.sih.dataservice.ml.dto.TopFeatureExplanation;
import com.sih.dataservice.ml.dto.TopNodeExplanation;
import com.sih.dataservice.streaming.dto.StartStreamingRequest;
import com.sih.dataservice.streaming.dto.StreamAlertDto;
import com.sih.dataservice.streaming.dto.StreamingStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamingReplayServiceTest {

    @Mock
    private AnomalyGateService anomalyGateService;

    @Mock
    private TemporalGraphEngine graphEngine;

    @Mock
    private ModelClient modelClient;

    @Mock
    private SyntheticDataGenerator syntheticDataGenerator;

    private ObjectMapper objectMapper;
    private StreamingReplayService service;

    private FinancialEntity sender;
    private FinancialEntity receiver;
    private Transaction normalTx;
    private Transaction muleTx;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new StreamingReplayService(
                anomalyGateService,
                graphEngine,
                modelClient,
                syntheticDataGenerator,
                objectMapper
        );

        sender = new FinancialEntity("sender_hash", "enc_sender", null, EntityType.ACCOUNT);
        sender.setId(UUID.randomUUID());

        receiver = new FinancialEntity("receiver_hash", "enc_receiver", null, EntityType.ACCOUNT);
        receiver.setId(UUID.randomUUID());

        normalTx = new Transaction("UTR-NORM-1", sender, receiver, BigDecimal.valueOf(1500), Instant.now(), TransactionSource.STREAM);
        normalTx.setId(UUID.randomUUID());

        muleTx = new Transaction("UTR-MULE-1", sender, receiver, BigDecimal.valueOf(95000), Instant.now(), TransactionSource.STREAM);
        muleTx.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("registerEmitter establishes SSE client and delivers connection event")
    void testRegisterEmitter() {
        SseEmitter emitter = service.registerEmitter();
        assertThat(emitter).isNotNull();

        StreamingStatusDto status = service.getStatus();
        assertThat(status.getConnectedClientsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Normal transaction does not trip gate and skips Stage 2 heavy inference (FR-GATE-2)")
    void testProcessTransaction_NormalPassesThrough() {
        GateEvaluationResult normalResult = new GateEvaluationResult(sender.getId(), false, false, 0.4, 2, "Normal activity");
        when(anomalyGateService.evaluate(eq(sender.getId()), anyDouble(), any())).thenReturn(normalResult);
        when(anomalyGateService.evaluate(eq(receiver.getId()), anyDouble(), any())).thenReturn(normalResult);

        StreamAlertDto alert = service.processTransaction(normalTx);

        assertThat(alert).isNull();
        verifyNoInteractions(graphEngine);
        verifyNoInteractions(modelClient);

        StreamingStatusDto status = service.getStatus();
        assertThat(status.getTotalProcessed()).isEqualTo(1);
        assertThat(status.getGateTrippedCount()).isEqualTo(0);
        assertThat(status.getAlertsEmittedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Anomalous transaction trips gate and triggers Stage 2 subgraph inference (FR-GATE-2, FR-PRD-1)")
    void testProcessTransaction_AnomalyTriggersStage2() {
        GateEvaluationResult trippedResult = new GateEvaluationResult(sender.getId(), true, false, 4.2, 8, "Velocity burst detected");
        when(anomalyGateService.evaluate(eq(sender.getId()), anyDouble(), any())).thenReturn(trippedResult);

        Subgraph subgraph = new Subgraph(sender.getId(), Map.of(), Map.of(), List.of());
        when(graphEngine.extractSubgraph(eq(sender.getId()), eq(2), any(), any(), eq(50))).thenReturn(subgraph);

        PredictionResponse response = new PredictionResponse(0.89, 0.94,
                List.of(new TopNodeExplanation(sender.getId().toString(), 0.9)),
                List.of(new TopFeatureExplanation("velocity_10m", 0.8)),
                "graphsage-v1.0.0");
        when(modelClient.predict(any(PredictionRequest.class))).thenReturn(response);

        StreamAlertDto alert = service.processTransaction(muleTx);

        assertThat(alert).isNotNull();
        assertThat(alert.isGateTripped()).isTrue();
        assertThat(alert.getRiskScore()).isEqualTo(0.89);
        assertThat(alert.getConfidence()).isEqualTo(0.94);
        assertThat(alert.getModelVersion()).isEqualTo("graphsage-v1.0.0");
        assertThat(alert.getUtr()).isEqualTo("UTR-MULE-1");

        verify(graphEngine).extractSubgraph(eq(sender.getId()), eq(2), any(), any(), eq(50));
        verify(modelClient).predict(any(PredictionRequest.class));

        StreamingStatusDto status = service.getStatus();
        assertThat(status.getTotalProcessed()).isEqualTo(1);
        assertThat(status.getGateTrippedCount()).isEqualTo(1);
        assertThat(status.getAlertsEmittedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Stage 2 falls back to Anomaly Gate if model service is down (FR-PRD-3)")
    void testProcessTransaction_FallbackOnModelDown() {
        GateEvaluationResult trippedResult = new GateEvaluationResult(sender.getId(), true, false, 3.8, 7, "Velocity burst");
        when(anomalyGateService.evaluate(eq(sender.getId()), anyDouble(), any())).thenReturn(trippedResult);

        when(graphEngine.extractSubgraph(any(), anyInt(), any(), any(), anyInt()))
                .thenReturn(new Subgraph(sender.getId(), Map.of(), Map.of(), List.of()));
        when(modelClient.predict(any())).thenThrow(new RuntimeException("Connection refused: model-service:8001"));

        StreamAlertDto alert = service.processTransaction(muleTx);

        assertThat(alert).isNotNull();
        assertThat(alert.isGateTripped()).isTrue();
        assertThat(alert.getRiskScore()).isGreaterThan(0.8);
        assertThat(alert.getExplanation()).contains("Fallback to Anomaly Gate");
    }

    @Test
    @DisplayName("startReplay and stopReplay controls replay lifecycle")
    void testReplayLifecycle() {
        when(syntheticDataGenerator.generateSyntheticDataset(anyInt(), anyInt(), any()))
                .thenReturn(List.of(normalTx, muleTx));

        StartStreamingRequest req = new StartStreamingRequest("synthetic_small", 5.0, 10, false);
        StreamingStatusDto startStatus = service.startReplay(req);

        assertThat(startStatus.isRunning()).isTrue();
        assertThat(startStatus.getSpeedMultiplier()).isEqualTo(5.0);

        StreamingStatusDto stopStatus = service.stopReplay();
        assertThat(stopStatus.isRunning()).isFalse();
    }
}
