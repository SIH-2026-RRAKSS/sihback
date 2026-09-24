package com.sih.dataservice.ml.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class RetrainRequestDto {

    @NotBlank
    private String modelName; // "graphsage", "xgboost"

    private UUID snapshotId;

    private boolean useSyntheticData = true;

    public RetrainRequestDto() {
    }

    public RetrainRequestDto(String modelName, UUID snapshotId, boolean useSyntheticData) {
        this.modelName = modelName;
        this.snapshotId = snapshotId;
        this.useSyntheticData = useSyntheticData;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(UUID snapshotId) {
        this.snapshotId = snapshotId;
    }

    public boolean isUseSyntheticData() {
        return useSyntheticData;
    }

    public void setUseSyntheticData(boolean useSyntheticData) {
        this.useSyntheticData = useSyntheticData;
    }
}
