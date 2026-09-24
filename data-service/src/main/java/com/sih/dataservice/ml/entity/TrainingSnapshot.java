package com.sih.dataservice.ml.entity;

import com.sih.dataservice.users.entity.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "training_snapshots")
public class TrainingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "version", nullable = false, unique = true)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "case_count", nullable = false)
    private int caseCount = 0;

    @Column(name = "fraud_count", nullable = false)
    private int fraudCount = 0;

    @Column(name = "storage_uri", nullable = false, length = 1024)
    private String storageUri;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TrainingSnapshot() {
    }

    public TrainingSnapshot(int version, User creator, int caseCount, int fraudCount, String storageUri, String checksum) {
        this.version = version;
        this.creator = creator;
        this.caseCount = caseCount;
        this.fraudCount = fraudCount;
        this.storageUri = storageUri;
        this.checksum = checksum;
        this.createdAt = Instant.now();
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

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
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
