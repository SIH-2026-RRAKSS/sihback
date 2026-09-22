package com.sih.dataservice.gate.service;

import com.sih.dataservice.gate.model.AccountStatistics;
import com.sih.dataservice.gate.model.GateEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnomalyGateService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyGateService.class);

    private final Map<UUID, AccountStatistics> accountStats = new ConcurrentHashMap<>();

    private final double zScoreThreshold;
    private final int velocityThreshold;
    private final Duration velocityWindow;
    private final int minObservationsForColdStart;
    private final double coldStartAmountThreshold;
    private final int coldStartVelocityThreshold;

    public AnomalyGateService(
            @Value("${gate.zscore.threshold:3.0}") double zScoreThreshold,
            @Value("${gate.velocity.threshold:5}") int velocityThreshold,
            @Value("${gate.velocity.window.minutes:60}") long velocityWindowMinutes,
            @Value("${gate.coldstart.min.observations:5}") int minObservationsForColdStart,
            @Value("${gate.coldstart.amount.threshold:50000.0}") double coldStartAmountThreshold,
            @Value("${gate.coldstart.velocity.threshold:3}") int coldStartVelocityThreshold) {
        this.zScoreThreshold = zScoreThreshold;
        this.velocityThreshold = velocityThreshold;
        this.velocityWindow = Duration.ofMinutes(velocityWindowMinutes);
        this.minObservationsForColdStart = minObservationsForColdStart;
        this.coldStartAmountThreshold = coldStartAmountThreshold;
        this.coldStartVelocityThreshold = coldStartVelocityThreshold;
    }

    /**
     * Evaluates whether a transaction trips the Stage 1 Anomaly Gate (FR-GATE-1, FR-GATE-2).
     */
    public GateEvaluationResult evaluate(UUID entityId, double amount, Instant timestamp) {
        if (entityId == null) {
            return new GateEvaluationResult(null, false, false, 0.0, 0, "Null entity");
        }

        AccountStatistics stats = accountStats.computeIfAbsent(entityId, k -> new AccountStatistics());

        long priorCount = stats.getCount();
        boolean isColdStart = priorCount < minObservationsForColdStart;
        double zScore = isColdStart ? 0.0 : stats.calculateZScore(amount);

        // Update statistics with the current transaction
        stats.update(amount, timestamp, velocityWindow);
        int currentVelocity = stats.getVelocity(timestamp, velocityWindow);

        boolean tripsGate = false;
        StringBuilder reason = new StringBuilder();

        if (isColdStart) {
            // Cold start rule: use absolute amount and velocity thresholds (FR-GATE-1)
            if (amount >= coldStartAmountThreshold) {
                tripsGate = true;
                reason.append(String.format("Cold-start amount threshold exceeded: %.2f >= %.2f; ", amount, coldStartAmountThreshold));
            }
            if (currentVelocity >= coldStartVelocityThreshold) {
                tripsGate = true;
                reason.append(String.format("Cold-start velocity threshold exceeded: %d >= %d; ", currentVelocity, coldStartVelocityThreshold));
            }
            if (!tripsGate) {
                reason.append("Cold-start normal; ");
            }
        } else {
            // Warm account: rolling z-score and velocity limits
            if (zScore >= zScoreThreshold) {
                tripsGate = true;
                reason.append(String.format("Z-score anomaly detected: %.2f >= %.2f; ", zScore, zScoreThreshold));
            }
            if (currentVelocity >= velocityThreshold) {
                tripsGate = true;
                reason.append(String.format("Velocity limit exceeded: %d >= %d; ", currentVelocity, velocityThreshold));
            }
            if (!tripsGate) {
                reason.append("Warm normal; ");
            }
        }

        GateEvaluationResult result = new GateEvaluationResult(
                entityId, tripsGate, isColdStart, zScore, currentVelocity, reason.toString().trim());

        if (tripsGate) {
            log.info("GATE TRIPPED for entity={}: {}", entityId, result.getReason());
        }

        return result;
    }

    public GateEvaluationResult evaluateTransaction(UUID senderId, UUID receiverId, BigDecimal amount, Instant timestamp) {
        double amt = amount != null ? amount.doubleValue() : 0.0;
        GateEvaluationResult senderRes = evaluate(senderId, amt, timestamp);
        if (senderRes.isTripsGate()) {
            return senderRes;
        }
        return evaluate(receiverId, amt, timestamp);
    }

    public void reset() {
        accountStats.clear();
    }
}
