package com.sih.dataservice.ml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.entity.CaseEvent;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintAccount;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.CaseEventRepository;
import com.sih.dataservice.complaints.repository.ComplaintAccountRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.gate.model.GateEvaluationResult;
import com.sih.dataservice.gate.service.AnomalyGateService;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.model.GraphEdge;
import com.sih.dataservice.graph.model.GraphNode;
import com.sih.dataservice.graph.model.Subgraph;
import com.sih.dataservice.ml.client.ModelClient;
import com.sih.dataservice.ml.dto.*;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.entity.Prediction;
import com.sih.dataservice.ml.repository.ModelVersionRepository;
import com.sih.dataservice.ml.repository.PredictionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final ComplaintRepository complaintRepository;
    private final ComplaintAccountRepository complaintAccountRepository;
    private final PredictionRepository predictionRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final CaseEventRepository caseEventRepository;
    private final TemporalGraphEngine graphEngine;
    private final AnomalyGateService anomalyGateService;
    private final ModelClient modelClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PredictionService(
            ComplaintRepository complaintRepository,
            ComplaintAccountRepository complaintAccountRepository,
            PredictionRepository predictionRepository,
            ModelVersionRepository modelVersionRepository,
            CaseEventRepository caseEventRepository,
            TemporalGraphEngine graphEngine,
            AnomalyGateService anomalyGateService,
            @Qualifier("httpModelClient") ModelClient modelClient,
            AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.complaintRepository = complaintRepository;
        this.complaintAccountRepository = complaintAccountRepository;
        this.predictionRepository = predictionRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.caseEventRepository = caseEventRepository;
        this.graphEngine = graphEngine;
        this.anomalyGateService = anomalyGateService;
        this.modelClient = modelClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Scores a complaint: extracts K-hop subgraph, calls model-service, and falls back to
     * Stage 1 Anomaly Gate if model service is unreachable (FR-PRD-1, FR-PRD-2, FR-PRD-3).
     */
    @Transactional
    public Prediction scoreComplaint(UUID complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("Complaint not found for scoring"));

        List<ComplaintAccount> accounts = complaintAccountRepository.findByComplaintId(complaintId);
        UUID rootEntityId = null;
        if (!accounts.isEmpty()) {
            rootEntityId = accounts.get(0).getEntity().getId();
        }

        // 1. Extract K-hop subgraph around primary incident account
        Subgraph subgraph = null;
        if (rootEntityId != null) {
            subgraph = graphEngine.extractSubgraph(rootEntityId, 2, null, null, 50);
        }

        // 2. Prepare de-identified prediction payload
        PredictionRequest request = buildDeidentifiedRequest(subgraph);

        BigDecimal risk;
        BigDecimal confidence;
        String explanationJson;
        boolean modelAvailable;
        String modelVersionName = "graphsage-v1.0.0";

        try {
            // 3. Call Model Service
            PredictionResponse response = modelClient.predict(request);
            risk = BigDecimal.valueOf(response.getRisk()).setScale(4, RoundingMode.HALF_UP);
            confidence = BigDecimal.valueOf(response.getConfidence()).setScale(4, RoundingMode.HALF_UP);
            explanationJson = objectMapper.writeValueAsString(Map.of(
                    "top_nodes", response.getTopNodes(),
                    "top_features", response.getTopFeatures()
            ));
            modelAvailable = true;
            if (response.getModelVersion() != null) {
                modelVersionName = response.getModelVersion();
            }
        } catch (Exception e) {
            // 4. Fault tolerance: Fallback to Stage 1 gate score (FR-PRD-3)
            log.warn("Model inference failed for complaint={}. Falling back to Stage 1 gate: {}",
                    complaintId, e.getMessage());

            GateEvaluationResult gateResult = null;
            if (rootEntityId != null) {
                gateResult = anomalyGateService.evaluate(
                        rootEntityId, complaint.getAmount().doubleValue(), complaint.getIncidentTime());
            }

            boolean tripped = gateResult != null && gateResult.isTripsGate();
            risk = BigDecimal.valueOf(tripped ? 0.7500 : 0.2500);
            confidence = BigDecimal.valueOf(0.5000);
            modelAvailable = false;

            explanationJson = String.format("{\"fallback\":true,\"reason\":\"Model service unavailable\",\"gateTripped\":%b}", tripped);
        }

        // 5. Resolve active model version or create default candidate
        final String finalVersionName = modelVersionName;
        ModelVersion modelVersion = modelVersionRepository.findByNameAndStatus("graphsage", ModelVersionStatus.ACTIVE)
                .orElseGet(() -> modelVersionRepository.save(new ModelVersion(
                        "graphsage", finalVersionName, "{\"baseline\":true}", ModelVersionStatus.ACTIVE)));

        // 6. Persist Prediction
        Prediction prediction = new Prediction(
                complaint, modelVersion, risk, confidence, explanationJson, modelAvailable);
        Prediction saved = predictionRepository.save(prediction);

        // 7. Auto-triage: if status is FILED, transition to TRIAGED (design.md Section 6 workflow)
        if (complaint.getStatus() == ComplaintStatus.FILED) {
            complaint.setStatus(ComplaintStatus.TRIAGED);
            complaint.setUpdatedAt(Instant.now(clock));
            complaintRepository.save(complaint);

            CaseEvent event = new CaseEvent(
                    complaint, null, ComplaintStatus.FILED, ComplaintStatus.TRIAGED,
                    String.format("Auto-triaged after risk assessment: risk=%s, modelAvailable=%b", risk, modelAvailable)
            );
            caseEventRepository.save(event);
        }

        auditService.log(null, "SYSTEM", "SCORE_COMPLAINT", "COMPLAINT",
                complaintId.toString(),
                String.format("{\"risk\":%s,\"confidence\":%s,\"modelAvailable\":%b}", risk, confidence, modelAvailable),
                "127.0.0.1");

        return saved;
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

        List<PredictionEdgeDto> edgeDtos = new ArrayList<>();
        for (GraphEdge edge : subgraph.getEdges()) {
            String source = opaqueIdMap.get(edge.getSourceEntityId());
            String target = opaqueIdMap.get(edge.getTargetEntityId());
            if (source != null && target != null) {
                edgeDtos.add(new PredictionEdgeDto(
                        source, target, edge.getAmount().doubleValue(), edge.getTimestamp()));
            }
        }

        return new PredictionRequest("graphsage", nodeDtos, edgeDtos);
    }
}
