package com.sih.dataservice.ml.dto;

import java.time.Instant;
import java.util.List;

public class RetrainingDashboardDto {
    private long totalLabeledCases;
    private long casesInLatestSnapshot;
    private long newCasesSinceSnapshot;
    private Integer latestSnapshotVersion;
    private Instant latestSnapshotCreatedAt;
    private List<ModelVersionDetailDto> activeModels;
    private List<ModelVersionDetailDto> candidateModels;

    public RetrainingDashboardDto() {
    }

    public RetrainingDashboardDto(long totalLabeledCases, long casesInLatestSnapshot, long newCasesSinceSnapshot,
                                  Integer latestSnapshotVersion, Instant latestSnapshotCreatedAt,
                                  List<ModelVersionDetailDto> activeModels, List<ModelVersionDetailDto> candidateModels) {
        this.totalLabeledCases = totalLabeledCases;
        this.casesInLatestSnapshot = casesInLatestSnapshot;
        this.newCasesSinceSnapshot = newCasesSinceSnapshot;
        this.latestSnapshotVersion = latestSnapshotVersion;
        this.latestSnapshotCreatedAt = latestSnapshotCreatedAt;
        this.activeModels = activeModels;
        this.candidateModels = candidateModels;
    }

    public long getTotalLabeledCases() {
        return totalLabeledCases;
    }

    public void setTotalLabeledCases(long totalLabeledCases) {
        this.totalLabeledCases = totalLabeledCases;
    }

    public long getCasesInLatestSnapshot() {
        return casesInLatestSnapshot;
    }

    public void setCasesInLatestSnapshot(long casesInLatestSnapshot) {
        this.casesInLatestSnapshot = casesInLatestSnapshot;
    }

    public long getNewCasesSinceSnapshot() {
        return newCasesSinceSnapshot;
    }

    public void setNewCasesSinceSnapshot(long newCasesSinceSnapshot) {
        this.newCasesSinceSnapshot = newCasesSinceSnapshot;
    }

    public Integer getLatestSnapshotVersion() {
        return latestSnapshotVersion;
    }

    public void setLatestSnapshotVersion(Integer latestSnapshotVersion) {
        this.latestSnapshotVersion = latestSnapshotVersion;
    }

    public Instant getLatestSnapshotCreatedAt() {
        return latestSnapshotCreatedAt;
    }

    public void setLatestSnapshotCreatedAt(Instant latestSnapshotCreatedAt) {
        this.latestSnapshotCreatedAt = latestSnapshotCreatedAt;
    }

    public List<ModelVersionDetailDto> getActiveModels() {
        return activeModels;
    }

    public void setActiveModels(List<ModelVersionDetailDto> activeModels) {
        this.activeModels = activeModels;
    }

    public List<ModelVersionDetailDto> getCandidateModels() {
        return candidateModels;
    }

    public void setCandidateModels(List<ModelVersionDetailDto> candidateModels) {
        this.candidateModels = candidateModels;
    }
}
