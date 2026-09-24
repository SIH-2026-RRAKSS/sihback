package com.sih.dataservice.benchmarks.dto;

import java.time.Instant;

public class StreamingBenchmarkResult {

    private String datasetName;
    private int totalTransactions;
    private long streamDurationMs;
    private double throughputTxPerSec;
    private int gateTrippedCount;
    private int stage2EvaluatedCount;
    private double stage2ReductionRatio;
    private double avgGateLatencyMs;
    private double avgStage2LatencyMs;
    private Instant executionTimestamp;

    public StreamingBenchmarkResult() {
    }

    public StreamingBenchmarkResult(String datasetName, int totalTransactions, long streamDurationMs,
                                   double throughputTxPerSec, int gateTrippedCount, int stage2EvaluatedCount,
                                   double stage2ReductionRatio, double avgGateLatencyMs,
                                   double avgStage2LatencyMs, Instant executionTimestamp) {
        this.datasetName = datasetName;
        this.totalTransactions = totalTransactions;
        this.streamDurationMs = streamDurationMs;
        this.throughputTxPerSec = throughputTxPerSec;
        this.gateTrippedCount = gateTrippedCount;
        this.stage2EvaluatedCount = stage2EvaluatedCount;
        this.stage2ReductionRatio = stage2ReductionRatio;
        this.avgGateLatencyMs = avgGateLatencyMs;
        this.avgStage2LatencyMs = avgStage2LatencyMs;
        this.executionTimestamp = executionTimestamp;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public int getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(int totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public long getStreamDurationMs() {
        return streamDurationMs;
    }

    public void setStreamDurationMs(long streamDurationMs) {
        this.streamDurationMs = streamDurationMs;
    }

    public double getThroughputTxPerSec() {
        return throughputTxPerSec;
    }

    public void setThroughputTxPerSec(double throughputTxPerSec) {
        this.throughputTxPerSec = throughputTxPerSec;
    }

    public int getGateTrippedCount() {
        return gateTrippedCount;
    }

    public void setGateTrippedCount(int gateTrippedCount) {
        this.gateTrippedCount = gateTrippedCount;
    }

    public int getStage2EvaluatedCount() {
        return stage2EvaluatedCount;
    }

    public void setStage2EvaluatedCount(int stage2EvaluatedCount) {
        this.stage2EvaluatedCount = stage2EvaluatedCount;
    }

    public double getStage2ReductionRatio() {
        return stage2ReductionRatio;
    }

    public void setStage2ReductionRatio(double stage2ReductionRatio) {
        this.stage2ReductionRatio = stage2ReductionRatio;
    }

    public double getAvgGateLatencyMs() {
        return avgGateLatencyMs;
    }

    public void setAvgGateLatencyMs(double avgGateLatencyMs) {
        this.avgGateLatencyMs = avgGateLatencyMs;
    }

    public double getAvgStage2LatencyMs() {
        return avgStage2LatencyMs;
    }

    public void setAvgStage2LatencyMs(double avgStage2LatencyMs) {
        this.avgStage2LatencyMs = avgStage2LatencyMs;
    }

    public Instant getExecutionTimestamp() {
        return executionTimestamp;
    }

    public void setExecutionTimestamp(Instant executionTimestamp) {
        this.executionTimestamp = executionTimestamp;
    }
}
