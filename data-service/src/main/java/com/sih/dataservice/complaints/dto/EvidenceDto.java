package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.Evidence;
import java.time.Instant;
import java.util.UUID;

public class EvidenceDto {

    private UUID id;
    private String filePath;
    private String mimeType;
    private long sizeBytes;
    private String sha256;
    private UUID uploaderId;
    private Instant createdAt;

    public EvidenceDto() {
    }

    public static EvidenceDto fromEntity(Evidence evidence) {
        EvidenceDto dto = new EvidenceDto();
        dto.setId(evidence.getId());
        dto.setFilePath(evidence.getFilePath());
        dto.setMimeType(evidence.getMimeType());
        dto.setSizeBytes(evidence.getSizeBytes());
        dto.setSha256(evidence.getSha256());
        if (evidence.getUploader() != null) {
            dto.setUploaderId(evidence.getUploader().getId());
        }
        dto.setCreatedAt(evidence.getCreatedAt());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public UUID getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(UUID uploaderId) {
        this.uploaderId = uploaderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
