package com.sih.dataservice.ml.entity;

import com.sih.dataservice.complaints.entity.Complaint;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_version_id")
    private ModelVersion modelVersion;

    @Column(name = "risk", nullable = false, precision = 5, scale = 4)
    private BigDecimal risk;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation", columnDefinition = "jsonb", nullable = false)
    private String explanation = "{}";

    @Column(name = "model_available", nullable = false)
    private boolean modelAvailable = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Prediction() {
    }

    public Prediction(Complaint complaint, ModelVersion modelVersion, BigDecimal risk, BigDecimal confidence, String explanation, boolean modelAvailable) {
        this.complaint = complaint;
        this.modelVersion = modelVersion;
        this.risk = risk;
        this.confidence = confidence;
        this.explanation = explanation != null ? explanation : "{}";
        this.modelAvailable = modelAvailable;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public void setComplaint(Complaint complaint) {
        this.complaint = complaint;
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(ModelVersion modelVersion) {
        this.modelVersion = modelVersion;
    }

    public BigDecimal getRisk() {
        return risk;
    }

    public void setRisk(BigDecimal risk) {
        this.risk = risk;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isModelAvailable() {
        return modelAvailable;
    }

    public void setModelAvailable(boolean modelAvailable) {
        this.modelAvailable = modelAvailable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
