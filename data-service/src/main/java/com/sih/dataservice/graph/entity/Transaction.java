package com.sih.dataservice.graph.entity;

import com.sih.dataservice.bankupload.entity.BankUpload;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.FinancialEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "utr", nullable = false, unique = true, length = 100)
    private String utr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private FinancialEntity sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private FinancialEntity receiver;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private TransactionSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id")
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private BankUpload upload;

    @Column(name = "ground_truth_label", length = 30)
    private String groundTruthLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Transaction() {
    }

    public Transaction(String utr, FinancialEntity sender, FinancialEntity receiver,
                       BigDecimal amount, Instant timestamp, TransactionSource source) {
        this.utr = utr;
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.timestamp = timestamp;
        this.source = source;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUtr() {
        return utr;
    }

    public void setUtr(String utr) {
        this.utr = utr;
    }

    public FinancialEntity getSender() {
        return sender;
    }

    public void setSender(FinancialEntity sender) {
        this.sender = sender;
    }

    public FinancialEntity getReceiver() {
        return receiver;
    }

    public void setReceiver(FinancialEntity receiver) {
        this.receiver = receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public TransactionSource getSource() {
        return source;
    }

    public void setSource(TransactionSource source) {
        this.source = source;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public void setComplaint(Complaint complaint) {
        this.complaint = complaint;
    }

    public BankUpload getUpload() {
        return upload;
    }

    public void setUpload(BankUpload upload) {
        this.upload = upload;
    }

    public String getGroundTruthLabel() {
        return groundTruthLabel;
    }

    public void setGroundTruthLabel(String groundTruthLabel) {
        this.groundTruthLabel = groundTruthLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
