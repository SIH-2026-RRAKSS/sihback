package com.sih.dataservice.ml.entity;

import com.sih.dataservice.users.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_versions")
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 50)
    private String name; // 'graphsage', 'xgboost'

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb", nullable = false)
    private String metrics = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ModelVersionStatus status = ModelVersionStatus.CANDIDATE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_by")
    private User promotedBy;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ModelVersion() {
    }

    public ModelVersion(String name, String version, String metrics, ModelVersionStatus status) {
        this.name = name;
        this.version = version;
        this.metrics = metrics != null ? metrics : "{}";
        this.status = status;
        this.createdAt = Instant.now();
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

    public String getMetrics() {
        return metrics;
    }

    public void setMetrics(String metrics) {
        this.metrics = metrics;
    }

    public ModelVersionStatus getStatus() {
        return status;
    }

    public void setStatus(ModelVersionStatus status) {
        this.status = status;
    }

    public User getPromotedBy() {
        return promotedBy;
    }

    public void setPromotedBy(User promotedBy) {
        this.promotedBy = promotedBy;
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
