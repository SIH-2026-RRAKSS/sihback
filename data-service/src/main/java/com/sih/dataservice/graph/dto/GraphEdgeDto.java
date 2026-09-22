package com.sih.dataservice.graph.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class GraphEdgeDto {

    private UUID transactionId;
    private String utr;
    private UUID sourceEntityId;
    private UUID targetEntityId;
    private BigDecimal amount;
    private Instant timestamp;

    public GraphEdgeDto() {
    }

    public GraphEdgeDto(UUID transactionId, String utr, UUID sourceEntityId, UUID targetEntityId, BigDecimal amount, Instant timestamp) {
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

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public String getUtr() {
        return utr;
    }

    public void setUtr(String utr) {
        this.utr = utr;
    }

    public UUID getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(UUID sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public UUID getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(UUID targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
