package com.sih.dataservice.graph.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class GraphEdge {

    private final UUID transactionId;
    private final String utr;
    private final UUID sourceEntityId;
    private final UUID targetEntityId;
    private final BigDecimal amount;
    private final Instant timestamp;

    public GraphEdge(UUID transactionId, String utr, UUID sourceEntityId, UUID targetEntityId, BigDecimal amount, Instant timestamp) {
        this.transactionId = transactionId;
        this.utr = utr;
        this.sourceEntityId = sourceEntityId;
        this.targetEntityId = targetEntityId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getUtr() {
        return utr;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public UUID getTargetEntityId() {
        return targetEntityId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphEdge graphEdge)) return false;
        return Objects.equals(transactionId, graphEdge.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }
}
