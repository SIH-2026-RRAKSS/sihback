package com.sih.dataservice.ml.dto;

import java.time.Instant;

public class PredictionEdgeDto {

    private String source;
    private String target;
    private double amount;
    private Instant timestamp;

    public PredictionEdgeDto() {
    }

    public PredictionEdgeDto(String source, String target, double amount, Instant timestamp) {
        this.source = source;
        this.target = target;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
