package com.sih.dataservice.ml.dto;

import com.sih.dataservice.ml.entity.TrainingSnapshot;

import java.time.Instant;
import java.util.UUID;

public class TrainingSnapshotDto {
    private UUID id;
    private int version;
    private String creatorName;
    private int caseCount;
    private int fraudCount;
    private String storageUri;
    private String checksum;
    private Instant createdAt;

    public TrainingSnapshotDto() {
    }

    public static TrainingSnapshotDto fromEntity(TrainingSnapshot snapshot) {
        TrainingSnapshotDto dto = new TrainingSnapshotDto();
        dto.setId(snapshot.getId());
        dto.setVersion(snapshot.getVersion());
        dto.setCreatorName(snapshot.getCreator() != null ? snapshot.getCreator().getName() : "System");
        dto.setCaseCount(snapshot.getCaseCount());
        dto.setFraudCount(snapshot.getFraudCount());
        dto.setStorageUri(snapshot.getStorageUri());
        dto.setChecksum(snapshot.getChecksum());
        dto.setCreatedAt(snapshot.getCreatedAt());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public int getCaseCount() {
        return caseCount;
    }

    public void setCaseCount(int caseCount) {
        this.caseCount = caseCount;
    }

    public int getFraudCount() {
        return fraudCount;
    }

    public void setFraudCount(int fraudCount) {
        this.fraudCount = fraudCount;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
