package com.sih.dataservice.graph.dto;

import com.sih.dataservice.complaints.entity.EntityType;

import java.time.Instant;
import java.util.UUID;

public class GraphNodeDto {

    private UUID entityId;
    private String accountHash;
    private boolean masked;
    private UUID bankId;
    private String bankName;
    private EntityType type;
    private int hopDepth;
    private double riskScore;
    private Instant firstSeen;
    private Instant lastSeen;

    public GraphNodeDto() {
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getAccountHash() {
        return accountHash;
    }

    public void setAccountHash(String accountHash) {
        this.accountHash = accountHash;
    }

    public boolean isMasked() {
        return masked;
    }

    public void setMasked(boolean masked) {
        this.masked = masked;
    }

    public UUID getBankId() {
        return bankId;
    }

    public void setBankId(UUID bankId) {
        this.bankId = bankId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public EntityType getType() {
        return type;
    }

    public void setType(EntityType type) {
        this.type = type;
    }

    public int getHopDepth() {
        return hopDepth;
    }

    public void setHopDepth(int hopDepth) {
        this.hopDepth = hopDepth;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
