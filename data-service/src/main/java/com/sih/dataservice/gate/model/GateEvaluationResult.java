package com.sih.dataservice.gate.model;

import java.util.UUID;

public class GateEvaluationResult {

    private final UUID entityId;
    private final boolean tripsGate;
    private final boolean isColdStart;
    private final double zScore;
    private final int velocity;
    private final String reason;

    public GateEvaluationResult(UUID entityId, boolean tripsGate, boolean isColdStart, double zScore, int velocity, String reason) {
        this.entityId = entityId;
        this.tripsGate = tripsGate;
        this.isColdStart = isColdStart;
        this.zScore = zScore;
        this.velocity = velocity;
        this.reason = reason;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public boolean isTripsGate() {
        return tripsGate;
    }

    public boolean isColdStart() {
        return isColdStart;
    }

    public double getzScore() {
        return zScore;
    }

    public int getVelocity() {
        return velocity;
    }

    public String getReason() {
        return reason;
    }
}
