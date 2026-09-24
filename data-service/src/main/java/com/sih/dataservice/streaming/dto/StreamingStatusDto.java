package com.sih.dataservice.streaming.dto;

public class StreamingStatusDto {

    private boolean running;
    private double speedMultiplier;
    private long totalProcessed;
    private long gateTrippedCount;
    private long alertsEmittedCount;
    private int connectedClientsCount;
    private String currentDataset;

    public StreamingStatusDto() {
    }

    public StreamingStatusDto(boolean running, double speedMultiplier, long totalProcessed,
                              long gateTrippedCount, long alertsEmittedCount, int connectedClientsCount,
                              String currentDataset) {
        this.running = running;
        this.speedMultiplier = speedMultiplier;
        this.totalProcessed = totalProcessed;
        this.gateTrippedCount = gateTrippedCount;
        this.alertsEmittedCount = alertsEmittedCount;
        this.connectedClientsCount = connectedClientsCount;
        this.currentDataset = currentDataset;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(long totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public long getGateTrippedCount() {
        return gateTrippedCount;
    }

    public void setGateTrippedCount(long gateTrippedCount) {
        this.gateTrippedCount = gateTrippedCount;
    }

    public long getAlertsEmittedCount() {
        return alertsEmittedCount;
    }

    public void setAlertsEmittedCount(long alertsEmittedCount) {
        this.alertsEmittedCount = alertsEmittedCount;
    }

    public int getConnectedClientsCount() {
        return connectedClientsCount;
    }

    public void setConnectedClientsCount(int connectedClientsCount) {
        this.connectedClientsCount = connectedClientsCount;
    }

    public String getCurrentDataset() {
        return currentDataset;
    }

    public void setCurrentDataset(String currentDataset) {
        this.currentDataset = currentDataset;
    }
}
