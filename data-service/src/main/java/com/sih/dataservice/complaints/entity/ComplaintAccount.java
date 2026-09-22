package com.sih.dataservice.complaints.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "complaint_accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"complaint_id", "entity_id", "role"})
})
public class ComplaintAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private FinancialEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AccountRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ComplaintAccount() {
    }

    public ComplaintAccount(Complaint complaint, FinancialEntity entity, AccountRole role) {
        this.complaint = complaint;
        this.entity = entity;
        this.role = role;
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

    public FinancialEntity getEntity() {
        return entity;
    }

    public void setEntity(FinancialEntity entity) {
        this.entity = entity;
    }

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
