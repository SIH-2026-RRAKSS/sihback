package com.sih.dataservice.complaints.entity;

import com.sih.dataservice.users.entity.Bank;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "entities")
public class FinancialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_hash", nullable = false, unique = true, length = 64)
    private String accountHash;

    @Column(name = "account_encrypted", nullable = false, length = 512)
    private String accountEncrypted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private EntityType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public FinancialEntity() {
    }

    public FinancialEntity(String accountHash, String accountEncrypted, Bank bank, EntityType type) {
        this.accountHash = accountHash;
        this.accountEncrypted = accountEncrypted;
        this.bank = bank;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccountHash() {
        return accountHash;
    }

    public void setAccountHash(String accountHash) {
        this.accountHash = accountHash;
    }

    public String getAccountEncrypted() {
        return accountEncrypted;
    }

    public void setAccountEncrypted(String accountEncrypted) {
        this.accountEncrypted = accountEncrypted;
    }

    public Bank getBank() {
        return bank;
    }

    public void setBank(Bank bank) {
        this.bank = bank;
    }

    public EntityType getType() {
        return type;
    }

    public void setType(EntityType type) {
        this.type = type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
