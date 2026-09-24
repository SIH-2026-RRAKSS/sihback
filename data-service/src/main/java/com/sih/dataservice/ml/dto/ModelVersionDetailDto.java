package com.sih.dataservice.ml.dto;

import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;

import java.time.Instant;
import java.util.UUID;

public class ModelVersionDetailDto {
    private UUID id;
    private String name;
    private String version;
    private ModelVersionStatus status;
    private String metrics;
    private Integer snapshotVersion;
    private String promotedByName;
    private Instant promotedAt;
    private Instant createdAt;

    public ModelVersionDetailDto() {
    }

    public static ModelVersionDetailDto fromEntity(ModelVersion mv) {
        ModelVersionDetailDto dto = new ModelVersionDetailDto();
        dto.setId(mv.getId());
        dto.setName(mv.getName());
        dto.setVersion(mv.getVersion());
        dto.setStatus(mv.getStatus());
        dto.setMetrics(mv.getMetrics());
        dto.setSnapshotVersion(mv.getSnapshot() != null ? mv.getSnapshot().getVersion() : null);
        dto.setPromotedByName(mv.getPromotedBy() != null ? mv.getPromotedBy().getName() : null);
        dto.setPromotedAt(mv.getPromotedAt());
        dto.setCreatedAt(mv.getCreatedAt());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ModelVersionStatus getStatus() {
        return status;
    }

    public void setStatus(ModelVersionStatus status) {
        this.status = status;
    }

    public String getMetrics() {
        return metrics;
    }

    public void setMetrics(String metrics) {
        this.metrics = metrics;
    }

    public Integer getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(Integer snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public String getPromotedByName() {
        return promotedByName;
    }

    public void setPromotedByName(String promotedByName) {
        this.promotedByName = promotedByName;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(Instant promotedAt) {
        this.promotedAt = promotedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
