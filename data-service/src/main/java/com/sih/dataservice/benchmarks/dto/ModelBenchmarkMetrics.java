package com.sih.dataservice.benchmarks.dto;

public class ModelBenchmarkMetrics {

    private String modelName;
    private double precision;
    private double recall;
    private double f1Score;
    private double accuracy;
    private double prAuc;
    private double throughputTxPerSec;
    private double avgLatencyMs;
    private int totalEvaluated;

    public ModelBenchmarkMetrics() {
    }

    public ModelBenchmarkMetrics(String modelName, double precision, double recall, double f1Score,
                                 double accuracy, double prAuc, double throughputTxPerSec,
                                 double avgLatencyMs, int totalEvaluated) {
        this.modelName = modelName;
        this.precision = precision;
        this.recall = recall;
        this.f1Score = f1Score;
        this.accuracy = accuracy;
        this.prAuc = prAuc;
        this.throughputTxPerSec = throughputTxPerSec;
        this.avgLatencyMs = avgLatencyMs;
        this.totalEvaluated = totalEvaluated;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public double getPrecision() {
        return precision;
    }

    public void setPrecision(double precision) {
        this.precision = precision;
    }

    public double getRecall() {
        return recall;
    }

    public void setRecall(double recall) {
        this.recall = recall;
    }

    public double getF1Score() {
        return f1Score;
    }

    public void setF1Score(double f1Score) {
        this.f1Score = f1Score;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public double getPrAuc() {
        return prAuc;
    }

    public void setPrAuc(double prAuc) {
        this.prAuc = prAuc;
    }

    public double getThroughputTxPerSec() {
        return throughputTxPerSec;
    }

    public void setThroughputTxPerSec(double throughputTxPerSec) {
        this.throughputTxPerSec = throughputTxPerSec;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public int getTotalEvaluated() {
        return totalEvaluated;
    }

    public void setTotalEvaluated(int totalEvaluated) {
        this.totalEvaluated = totalEvaluated;
    }
}
