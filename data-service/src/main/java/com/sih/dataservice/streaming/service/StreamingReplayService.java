package com.sih.dataservice.streaming.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.complaints.entity.FinancialEntity;
import com.sih.dataservice.datasets.SyntheticDataGenerator;
import com.sih.dataservice.gate.model.GateEvaluationResult;
import com.sih.dataservice.gate.service.AnomalyGateService;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.model.GraphEdge;
import com.sih.dataservice.graph.model.GraphNode;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.ml.client.ModelClient;
import com.sih.dataservice.ml.dto.PredictionEdgeDto;
import com.sih.dataservice.ml.dto.PredictionNodeDto;
import com.sih.dataservice.ml.dto.PredictionRequest;
import com.sih.dataservice.ml.dto.PredictionResponse;
import com.sih.dataservice.streaming.dto.StartStreamingRequest;
import com.sih.dataservice.streaming.dto.StreamAlertDto;
import com.sih.dataservice.streaming.dto.StreamingStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service managing real-time transaction stream replay and Server-Sent Events (SSE).
 * Implements two-stage evaluation: Stage 1 Anomaly Gate -> Stage 2 Subgraph Inference (FR-STR-1, FR-GATE-1, FR-GATE-2).
 */
@Service
public class StreamingReplayService {

    private static final Logger log = LoggerFactory.getLogger(StreamingReplayService.class);

    private final AnomalyGateService anomalyGateService;
    private final TemporalGraphEngine graphEngine;
    private final ModelClient modelClient;
    private final SyntheticDataGenerator syntheticDataGenerator;
    private final ObjectMapper objectMapper;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong gateTrippedCount = new AtomicLong(0);
    private final AtomicLong alertsEmittedCount = new AtomicLong(0);

    private volatile double currentSpeedMultiplier = 1.0;
    private volatile String currentDataset = "synthetic_small";
    private ScheduledExecutorService executorService;
    private Future<?> currentReplayTask;

    public StreamingReplayService(
            AnomalyGateService anomalyGateService,
            TemporalGraphEngine graphEngine,
            ModelClient modelClient,
            SyntheticDataGenerator syntheticDataGenerator,
            ObjectMapper objectMapper) {
        this.anomalyGateService = anomalyGateService;
        this.graphEngine = graphEngine;
        this.modelClient = modelClient;
        this.syntheticDataGenerator = syntheticDataGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a new SSE client emitter with keepalive heartbeat (FR-STR-1).
     */
    public SseEmitter registerEmitter() {
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE client completed, total connected: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE client timed out, total connected: {}", emitters.size());
        });

        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE client error, removed: {}", e.getMessage());
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("message", "Connected to transaction live stream", "timestamp", Instant.now().toString())));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Starts background replay of a transaction dataset at specified speed (FR-STR-1).
     */
    public synchronized StreamingStatusDto startReplay(StartStreamingRequest request) {
        if (running.get()) {
            stopReplay();
        }

        this.currentDataset = request.getDatasetName();
        this.currentSpeedMultiplier = Math.max(0.1, request.getSpeedMultiplier());
        this.running.set(true);

        List<Transaction> rawDataset = syntheticDataGenerator.generateSyntheticDataset(30, 5, Instant.now().minusSeconds(86400 * 2));
        List<Transaction> dataset = new ArrayList<>(rawDataset != null ? rawDataset : List.of());
        dataset.sort(Comparator.comparing(Transaction::getTimestamp));

        int limit = Math.min(request.getMaxTransactions(), dataset.size());
        List<Transaction> replayList = dataset.subList(0, limit);

        broadcastEvent("STREAM_START", Map.of(
                "dataset", currentDataset,
                "totalTransactions", replayList.size(),
                "speedMultiplier", currentSpeedMultiplier,
                "timestamp", Instant.now().toString()
        ));

        long delayMs = Math.max(50, (long) (1000.0 / currentSpeedMultiplier));
        executorService = Executors.newSingleThreadScheduledExecutor();

        final Iterator<Transaction> iterator = replayList.iterator();
        currentReplayTask = executorService.scheduleAtFixedRate(() -> {
            if (!running.get() || !iterator.hasNext()) {
                if (request.isLoop() && running.get()) {
                    // loop restart
                    startReplay(request);
                } else {
                    stopReplay();
                }
                return;
            }

            Transaction tx = iterator.next();
            processTransaction(tx);
        }, 0, delayMs, TimeUnit.MILLISECONDS);

        log.info("Started streaming replay dataset={} speedMultiplier={} count={}", currentDataset, currentSpeedMultiplier, limit);
        return getStatus();
    }

    /**
     * Stops ongoing stream replay.
     */
    public synchronized StreamingStatusDto stopReplay() {
        running.set(false);
        if (currentReplayTask != null) {
            currentReplayTask.cancel(true);
            currentReplayTask = null;
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = null;
        }

        broadcastEvent("STREAM_STOP", Map.of(
                "message", "Transaction replay stopped",
                "totalProcessed", totalProcessed.get(),
                "alertsEmitted", alertsEmittedCount.get(),
                "timestamp", Instant.now().toString()
        ));

        log.info("Stopped streaming replay");
        return getStatus();
    }

    /**
     * Processes a single transaction in step mode (FR-STR-1, FR-GATE-1, FR-GATE-2).
     */
    public StreamAlertDto step(Transaction tx) {
        return processTransaction(tx);
    }

    /**
     * Core two-stage pipeline:
     * Stage 1: Fast O(1) Anomaly Gate.
     * Stage 2: Selective Subgraph Extraction + Model Inference (FR-GATE-2).
     */
    public StreamAlertDto processTransaction(Transaction tx) {
        totalProcessed.incrementAndGet();

        FinancialEntity sender = tx.getSender();
        FinancialEntity receiver = tx.getReceiver();
        double amount = tx.getAmount() != null ? tx.getAmount().doubleValue() : 0.0;
        Instant txTime = tx.getTimestamp() != null ? tx.getTimestamp() : Instant.now();

        // Stage 1: Evaluate Anomaly Gate on sender and receiver
        GateEvaluationResult gateResult = null;
        UUID trippedEntityId = null;

        if (sender != null && sender.getId() != null) {
            gateResult = anomalyGateService.evaluate(sender.getId(), amount, txTime);
            if (gateResult.isTripsGate()) {
                trippedEntityId = sender.getId();
            }
        }

        if ((gateResult == null || !gateResult.isTripsGate()) && receiver != null && receiver.getId() != null) {
            GateEvaluationResult receiverResult = anomalyGateService.evaluate(receiver.getId(), amount, txTime);
            if (receiverResult.isTripsGate()) {
                gateResult = receiverResult;
                trippedEntityId = receiver.getId();
            }
        }

        // If gate did not trip, normal transaction -> broadcast and skip Stage 2 heavy ML
        if (gateResult == null || !gateResult.isTripsGate()) {
            broadcastEvent("TRANSACTION", Map.of(
                    "utr", tx.getUtr(),
                    "amount", amount,
                    "timestamp", txTime.toString(),
                    "gateTripped", false
            ));
            return null;
        }

        // Stage 2: Gate tripped! Run subgraph extraction and model inference (FR-GATE-2)
        gateTrippedCount.incrementAndGet();

        double riskScore = 0.5;
        double confidence = 0.5;
        String modelVer = "anomaly-gate-heuristic";
        List<String> topNodes = List.of(trippedEntityId.toString());
        List<String> topFeatures = List.of("velocity", "rolling_z_score");
        String explanation = gateResult.getReason();

        try {
            Subgraph subgraph = graphEngine.extractSubgraph(trippedEntityId, 2, txTime.minusSeconds(86400 * 2), txTime, 50);
            PredictionRequest predReq = buildDeidentifiedRequest(subgraph);
            PredictionResponse predResp = modelClient.predict(predReq);

            riskScore = predResp.getRisk();
            confidence = predResp.getConfidence();
            modelVer = predResp.getModelVersion();
            if (predResp.getTopNodes() != null && !predResp.getTopNodes().isEmpty()) {
                topNodes = predResp.getTopNodes().stream().map(com.sih.dataservice.ml.dto.TopNodeExplanation::getId).toList();
            }
            if (predResp.getTopFeatures() != null && !predResp.getTopFeatures().isEmpty()) {
                topFeatures = predResp.getTopFeatures().stream().map(com.sih.dataservice.ml.dto.TopFeatureExplanation::getName).toList();
            }
            explanation = String.format("Gate tripped (%s). Model evaluated risk=%.3f, confidence=%.3f",
                    gateResult.getReason(), riskScore, confidence);
        } catch (Exception e) {
            log.warn("Model service unavailable during stream inference: {}. Using gate score fallback (FR-PRD-3)", e.getMessage());
            riskScore = gateResult.isTripsGate() ? 0.85 : 0.20;
            confidence = 0.70;
            explanation = "Model service unavailable. Fallback to Anomaly Gate: " + gateResult.getReason();
        }

        StreamAlertDto alert = new StreamAlertDto();
        alert.setTransactionId(tx.getId());
        alert.setUtr(tx.getUtr());
        alert.setSenderHash(sender != null ? sender.getAccountHash() : "unknown");
        alert.setReceiverHash(receiver != null ? receiver.getAccountHash() : "unknown");
        alert.setAmount(tx.getAmount());
        alert.setTimestamp(txTime);
        alert.setGateTripped(true);
        alert.setzScore(gateResult.getzScore());
        alert.setVelocity10m(gateResult.getVelocity());
        alert.setGateReason(gateResult.getReason());
        alert.setRiskScore(riskScore);
        alert.setConfidence(confidence);
        alert.setModelVersion(modelVer);
        alert.setTopNodes(topNodes);
        alert.setTopFeatures(topFeatures);
        alert.setExplanation(explanation);

        alertsEmittedCount.incrementAndGet();
        broadcastEvent("ALERT", alert);

        log.info("STREAM ALERT: utr={} amount={} risk={}", tx.getUtr(), amount, riskScore);
        return alert;
    }

    /**
     * Returns live telemetry and statistics of the streaming engine.
     */
    public StreamingStatusDto getStatus() {
        return new StreamingStatusDto(
                running.get(),
                currentSpeedMultiplier,
                totalProcessed.get(),
                gateTrippedCount.get(),
                alertsEmittedCount.get(),
                emitters.size(),
                currentDataset
        );
    }

    public void resetCounts() {
        totalProcessed.set(0);
        gateTrippedCount.set(0);
        alertsEmittedCount.set(0);
    }

    private void broadcastEvent(String eventName, Object data) {
        if (emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private PredictionRequest buildDeidentifiedRequest(Subgraph subgraph) {
        if (subgraph == null || subgraph.getNodes().isEmpty()) {
            return new PredictionRequest("graphsage", List.of(), List.of());
        }
        Map<UUID, String> opaqueIdMap = new HashMap<>();
        List<PredictionNodeDto> nodeDtos = new ArrayList<>();
        int index = 0;
        for (GraphNode node : subgraph.getNodes().values()) {
            String opaqueId = "node-" + index++;
            opaqueIdMap.put(node.getEntityId(), opaqueId);
            Map<String, Double> features = new HashMap<>();
            features.put("risk_score", node.getRiskScore());
            features.put("hop_depth", (double) subgraph.getNodeDepths().getOrDefault(node.getEntityId(), 0));
            nodeDtos.add(new PredictionNodeDto(opaqueId, features));
        }
        List<GraphEdge> edges = subgraph.getEdges();
        List<PredictionEdgeDto> edgeDtos = new ArrayList<>();
        if (edges != null) {
            for (GraphEdge edge : edges) {
                String source = opaqueIdMap.get(edge.getSourceEntityId());
                String target = opaqueIdMap.get(edge.getTargetEntityId());
                if (source != null && target != null) {
                    edgeDtos.add(new PredictionEdgeDto(source, target, edge.getAmount().doubleValue(), edge.getTimestamp()));
                }
            }
        }
        return new PredictionRequest("graphsage", nodeDtos, edgeDtos);
    }
}
