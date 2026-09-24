package com.sih.dataservice.streaming.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class StreamAlertDto {

    private UUID transactionId;
    private String utr;
    private String senderHash;
    private String receiverHash;
    private BigDecimal amount;
    private Instant timestamp;
    private boolean gateTripped;
    private double zScore;
    private int velocity10m;
    private String gateReason;
    private double riskScore;
    private double confidence;
    private String modelVersion;
    private List<String> topNodes;
    private List<String> topFeatures;
    private String explanation;

    public StreamAlertDto() {
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

    public String getSenderHash() {
        return senderHash;
    }

    public void setSenderHash(String senderHash) {
        this.senderHash = senderHash;
    }

    public String getReceiverHash() {
        return receiverHash;
    }

    public void setReceiverHash(String receiverHash) {
        this.receiverHash = receiverHash;
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

    public boolean isGateTripped() {
        return gateTripped;
    }

    public void setGateTripped(boolean gateTripped) {
        this.gateTripped = gateTripped;
    }

    public double getzScore() {
        return zScore;
    }

    public void setzScore(double zScore) {
        this.zScore = zScore;
    }

    public int getVelocity10m() {
        return velocity10m;
    }

    public void setVelocity10m(int velocity10m) {
        this.velocity10m = velocity10m;
    }

    public String getGateReason() {
        return gateReason;
    }

    public void setGateReason(String gateReason) {
        this.gateReason = gateReason;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public List<String> getTopNodes() {
        return topNodes;
    }

    public void setTopNodes(List<String> topNodes) {
        this.topNodes = topNodes;
    }

    public List<String> getTopFeatures() {
        return topFeatures;
    }

    public void setTopFeatures(List<String> topFeatures) {
        this.topFeatures = topFeatures;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
