package com.sih.dataservice.ml.dto;

import com.sih.dataservice.ml.entity.Prediction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PredictionDto {
    private UUID id;
    private UUID complaintId;
    private String modelVersion;
    private BigDecimal risk;
    private BigDecimal confidence;
    private String explanation;
    private boolean modelAvailable;
    private Instant createdAt;

    public PredictionDto() {
    }

    public PredictionDto(UUID id, UUID complaintId, String modelVersion, BigDecimal risk,
                         BigDecimal confidence, String explanation, boolean modelAvailable, Instant createdAt) {
        this.id = id;
        this.complaintId = complaintId;
        this.modelVersion = modelVersion;
        this.risk = risk;
        this.confidence = confidence;
        this.explanation = explanation;
        this.modelAvailable = modelAvailable;
        this.createdAt = createdAt;
    }

    public static PredictionDto fromEntity(Prediction prediction) {
        if (prediction == null) return null;
        String mvName = prediction.getModelVersion() != null
                ? prediction.getModelVersion().getName() + ":" + prediction.getModelVersion().getVersion()
                : "unknown";
        return new PredictionDto(
                prediction.getId(),
                prediction.getComplaint() != null ? prediction.getComplaint().getId() : null,
                mvName,
                prediction.getRisk(),
                prediction.getConfidence(),
                prediction.getExplanation(),
                prediction.isModelAvailable(),
                prediction.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getComplaintId() {
        return complaintId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public BigDecimal getRisk() {
        return risk;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean isModelAvailable() {
        return modelAvailable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
