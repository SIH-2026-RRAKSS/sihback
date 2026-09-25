package com.sih.dataservice.policy.dto;

import java.util.List;

public class PolicyEvaluationResultDto {
    private int totalEvaluatedCases;
    private double optimalThreshold;
    private double maxF1Score;
    private List<ThresholdEvaluationPoint> points;

    public PolicyEvaluationResultDto() {
    }

    public PolicyEvaluationResultDto(int totalEvaluatedCases, double optimalThreshold,
                                     double maxF1Score, List<ThresholdEvaluationPoint> points) {
        this.totalEvaluatedCases = totalEvaluatedCases;
        this.optimalThreshold = optimalThreshold;
        this.maxF1Score = maxF1Score;
        this.points = points;
    }

    public int getTotalEvaluatedCases() {
        return totalEvaluatedCases;
    }

    public double getOptimalThreshold() {
        return optimalThreshold;
    }

    public double getMaxF1Score() {
        return maxF1Score;
    }

    public List<ThresholdEvaluationPoint> getPoints() {
        return points;
    }
}
