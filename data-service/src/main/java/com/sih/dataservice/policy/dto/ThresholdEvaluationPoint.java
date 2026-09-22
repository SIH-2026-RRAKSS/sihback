package com.sih.dataservice.policy.dto;

public class ThresholdEvaluationPoint {
    private double threshold;
    private long truePositives;
    private long falsePositives;
    private long trueNegatives;
    private long falseNegatives;
    private double precision;
    private double recall;
    private double f1Score;

    public ThresholdEvaluationPoint() {
    }

    public ThresholdEvaluationPoint(double threshold, long truePositives, long falsePositives,
                                    long trueNegatives, long falseNegatives,
                                    double precision, double recall, double f1Score) {
        this.threshold = threshold;
        this.truePositives = truePositives;
        this.falsePositives = falsePositives;
        this.trueNegatives = trueNegatives;
        this.falseNegatives = falseNegatives;
        this.precision = precision;
        this.recall = recall;
        this.f1Score = f1Score;
    }

    public double getThreshold() {
        return threshold;
    }

    public long getTruePositives() {
        return truePositives;
    }

    public long getFalsePositives() {
        return falsePositives;
    }

    public long getTrueNegatives() {
        return trueNegatives;
    }

    public long getFalseNegatives() {
        return falseNegatives;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getF1Score() {
        return f1Score;
    }
}
