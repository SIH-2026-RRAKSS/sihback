package com.sih.dataservice.gate.service;

import com.sih.dataservice.gate.model.GateEvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyGateServiceTest {

    private AnomalyGateService gateService;
    private UUID accountId;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        // z-score threshold: 3.0, velocity threshold: 5, window: 60m, cold-start observations: 5, cold-start amount: 50000, cold-start velocity: 3
        gateService = new AnomalyGateService(3.0, 5, 60, 5, 50000.0, 3);
        accountId = UUID.randomUUID();
        baseTime = Instant.parse("2026-09-22T00:00:00Z");
    }

    @Test
    void coldStartAccountNormalAmountDoesNotTripGate() {
        // First transaction of 2,000 INR
        GateEvaluationResult result = gateService.evaluate(accountId, 2000.0, baseTime);

        assertThat(result.isColdStart()).isTrue();
        assertThat(result.isTripsGate()).isFalse();
    }

    @Test
    void coldStartAccountHugeAmountTripsGate() {
        // First transaction of 75,000 INR (exceeds 50,000 cold-start threshold)
        GateEvaluationResult result = gateService.evaluate(accountId, 75000.0, baseTime);

        assertThat(result.isColdStart()).isTrue();
        assertThat(result.isTripsGate()).isTrue();
        assertThat(result.getReason()).contains("Cold-start amount threshold exceeded");
    }

    @Test
    void warmAccountCalculatesZscoreAndTripsOnSpike() {
        // Train baseline on 10 normal transactions around 1,000 INR
        for (int i = 0; i < 10; i++) {
            Instant t = baseTime.plusSeconds(i * 3600); // 1 hour apart to avoid velocity trip
            gateService.evaluate(accountId, 1000.0 + (i % 2 == 0 ? 50 : -50), t);
        }

        // Normal transaction around 1,050 INR -> should not trip
        GateEvaluationResult normal = gateService.evaluate(accountId, 1050.0, baseTime.plusSeconds(86400));
        assertThat(normal.isColdStart()).isFalse();
        assertThat(normal.isTripsGate()).isFalse();

        // Massive spike transaction of 50,000 INR -> high z-score -> should trip
        GateEvaluationResult spike = gateService.evaluate(accountId, 50000.0, baseTime.plusSeconds(86400 + 3600));
        assertThat(spike.isColdStart()).isFalse();
        assertThat(spike.isTripsGate()).isTrue();
        assertThat(spike.getzScore()).isGreaterThan(3.0);
        assertThat(spike.getReason()).contains("Z-score anomaly detected");
    }

    @Test
    void burstTransactionsTripVelocityThreshold() {
        // 6 rapid transactions within 5 minutes -> exceeds velocity limit of 5
        GateEvaluationResult last = null;
        for (int i = 0; i < 6; i++) {
            last = gateService.evaluate(accountId, 500.0, baseTime.plusSeconds(i * 30));
        }

        assertThat(last).isNotNull();
        assertThat(last.isTripsGate()).isTrue();
        assertThat(last.getVelocity()).isGreaterThanOrEqualTo(3);
    }
}
