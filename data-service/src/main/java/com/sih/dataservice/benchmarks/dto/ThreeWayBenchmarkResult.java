package com.sih.dataservice.benchmarks.dto;

import java.time.Instant;

public class ThreeWayBenchmarkResult {

    private String datasetName;
    private int totalTransactions;
    private int muleTransactionsCount;
    private ModelBenchmarkMetrics gateOnly;
    private ModelBenchmarkMetrics xgboost;
    private ModelBenchmarkMetrics graphSage;
    private double gateReductionPercentage;
    private Instant executionTimestamp;

    public ThreeWayBenchmarkResult() {
    }

    public ThreeWayBenchmarkResult(String datasetName, int totalTransactions, int muleTransactionsCount,
                                  ModelBenchmarkMetrics gateOnly, ModelBenchmarkMetrics xgboost,
                                  ModelBenchmarkMetrics graphSage, double gateReductionPercentage,
                                  Instant executionTimestamp) {
        this.datasetName = datasetName;
        this.totalTransactions = totalTransactions;
        this.muleTransactionsCount = muleTransactionsCount;
        this.gateOnly = gateOnly;
        this.xgboost = xgboost;
        this.graphSage = graphSage;
        this.gateReductionPercentage = gateReductionPercentage;
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

    public int getMuleTransactionsCount() {
        return muleTransactionsCount;
    }

    public void setMuleTransactionsCount(int muleTransactionsCount) {
        this.muleTransactionsCount = muleTransactionsCount;
    }

    public ModelBenchmarkMetrics getGateOnly() {
        return gateOnly;
    }

    public void setGateOnly(ModelBenchmarkMetrics gateOnly) {
        this.gateOnly = gateOnly;
    }

    public ModelBenchmarkMetrics getXgboost() {
        return xgboost;
    }

    public void setXgboost(ModelBenchmarkMetrics xgboost) {
        this.xgboost = xgboost;
    }

    public ModelBenchmarkMetrics getGraphSage() {
        return graphSage;
    }

    public void setGraphSage(ModelBenchmarkMetrics graphSage) {
        this.graphSage = graphSage;
    }

    public double getGateReductionPercentage() {
        return gateReductionPercentage;
    }

    public void setGateReductionPercentage(double gateReductionPercentage) {
        this.gateReductionPercentage = gateReductionPercentage;
    }

    public Instant getExecutionTimestamp() {
        return executionTimestamp;
    }

    public void setExecutionTimestamp(Instant executionTimestamp) {
        this.executionTimestamp = executionTimestamp;
    }
}
