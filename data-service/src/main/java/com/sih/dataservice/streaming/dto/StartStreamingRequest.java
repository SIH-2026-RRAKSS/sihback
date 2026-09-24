package com.sih.dataservice.streaming.dto;

public class StartStreamingRequest {

    private String datasetName = "synthetic_small";
    private double speedMultiplier = 1.0;
    private int maxTransactions = 100;
    private boolean loop = false;

    public StartStreamingRequest() {
    }

    public StartStreamingRequest(String datasetName, double speedMultiplier, int maxTransactions, boolean loop) {
        this.datasetName = datasetName;
        this.speedMultiplier = speedMultiplier;
        this.maxTransactions = maxTransactions;
        this.loop = loop;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    public int getMaxTransactions() {
        return maxTransactions;
    }

    public void setMaxTransactions(int maxTransactions) {
        this.maxTransactions = maxTransactions;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }
}
