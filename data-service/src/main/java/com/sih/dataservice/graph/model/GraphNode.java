package com.sih.dataservice.graph.model;

import com.sih.dataservice.complaints.entity.EntityType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class GraphNode {

    private final UUID entityId;
    private final String accountHash;
    private final UUID bankId;
    private final EntityType type;
    private Instant firstSeen;
    private Instant lastSeen;
    private double riskScore = 0.0;

    public GraphNode(UUID entityId, String accountHash, UUID bankId, EntityType type, Instant timestamp) {
        this.entityId = entityId;
        this.accountHash = accountHash;
        this.bankId = bankId;
        this.type = type;
        this.firstSeen = timestamp;
        this.lastSeen = timestamp;
    }

    public void updateSeen(Instant timestamp) {
        if (timestamp != null) {
            if (firstSeen == null || timestamp.isBefore(firstSeen)) {
                firstSeen = timestamp;
            }
            if (lastSeen == null || timestamp.isAfter(lastSeen)) {
                lastSeen = timestamp;
            }
        }
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAccountHash() {
        return accountHash;
    }

    public UUID getBankId() {
        return bankId;
    }

    public EntityType getType() {
        return type;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphNode graphNode)) return false;
        return Objects.equals(entityId, graphNode.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }
}
