package com.sih.dataservice.stats.dto;

import java.math.BigDecimal;
import java.util.Map;

public class SystemStatsDto {
    private long totalComplaints;
    private Map<String, Long> complaintsByStatus;
    private BigDecimal totalAmount;
    private long totalPredictions;
    private long modelAvailablePredictions;
    private double modelAvailabilityRate;
    private String activeModelVersion;
    private long totalTransactions;

    public SystemStatsDto() {
    }

    public SystemStatsDto(long totalComplaints, Map<String, Long> complaintsByStatus, BigDecimal totalAmount,
                          long totalPredictions, long modelAvailablePredictions, double modelAvailabilityRate,
                          String activeModelVersion, long totalTransactions) {
        this.totalComplaints = totalComplaints;
        this.complaintsByStatus = complaintsByStatus;
        this.totalAmount = totalAmount;
        this.totalPredictions = totalPredictions;
        this.modelAvailablePredictions = modelAvailablePredictions;
        this.modelAvailabilityRate = modelAvailabilityRate;
        this.activeModelVersion = activeModelVersion;
        this.totalTransactions = totalTransactions;
    }

    public long getTotalComplaints() {
        return totalComplaints;
    }

    public Map<String, Long> getComplaintsByStatus() {
        return complaintsByStatus;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public long getTotalPredictions() {
        return totalPredictions;
    }

    public long getModelAvailablePredictions() {
        return modelAvailablePredictions;
    }

    public double getModelAvailabilityRate() {
        return modelAvailabilityRate;
    }

    public String getActiveModelVersion() {
        return activeModelVersion;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }
}
