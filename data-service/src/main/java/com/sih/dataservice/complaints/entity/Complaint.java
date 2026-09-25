package com.sih.dataservice.complaints.entity;

import com.sih.dataservice.users.entity.Jurisdiction;
import com.sih.dataservice.users.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "human_reference", nullable = false, unique = true, length = 50)
    private String humanReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complainant_id", nullable = false)
    private User complainant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jurisdiction_id")
    private Jurisdiction jurisdiction;

    @Column(name = "fraud_type", nullable = false, length = 50)
    private String fraudType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "incident_time", nullable = false)
    private Instant incidentTime;

    @Column(name = "description_original", nullable = false, columnDefinition = "TEXT")
    private String descriptionOriginal;

    @Column(name = "description_language", nullable = false, length = 10)
    private String descriptionLanguage = "en";

    @Column(name = "description_english", columnDefinition = "TEXT")
    private String descriptionEnglish;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ComplaintChannel channel = ComplaintChannel.WEB;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ComplaintStatus status = ComplaintStatus.FILED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_officer_id")
    private User assignedOfficer;

    @Enumerated(EnumType.STRING)
    @Column(name = "label", length = 30)
    private CaseLabel label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "labeled_by")
    private User labeledBy;

    @Column(name = "labeled_at")
    private Instant labeledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Complaint() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getHumanReference() {
        return humanReference;
    }

    public void setHumanReference(String humanReference) {
        this.humanReference = humanReference;
    }

    public User getComplainant() {
        return complainant;
    }

    public void setComplainant(User complainant) {
        this.complainant = complainant;
    }

    public Jurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(Jurisdiction jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getFraudType() {
        return fraudType;
    }

    public void setFraudType(String fraudType) {
        this.fraudType = fraudType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getIncidentTime() {
        return incidentTime;
    }

    public void setIncidentTime(Instant incidentTime) {
        this.incidentTime = incidentTime;
    }

    public String getDescriptionOriginal() {
        return descriptionOriginal;
    }

    public void setDescriptionOriginal(String descriptionOriginal) {
        this.descriptionOriginal = descriptionOriginal;
    }

    public String getDescriptionLanguage() {
        return descriptionLanguage;
    }

    public void setDescriptionLanguage(String descriptionLanguage) {
        this.descriptionLanguage = descriptionLanguage;
    }

    public String getDescriptionEnglish() {
        return descriptionEnglish;
    }

    public void setDescriptionEnglish(String descriptionEnglish) {
        this.descriptionEnglish = descriptionEnglish;
    }

    public ComplaintChannel getChannel() {
        return channel;
    }

    public void setChannel(ComplaintChannel channel) {
        this.channel = channel;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public User getAssignedOfficer() {
        return assignedOfficer;
    }

    public void setAssignedOfficer(User assignedOfficer) {
        this.assignedOfficer = assignedOfficer;
    }

    public CaseLabel getLabel() {
        return label;
    }

    public void setLabel(CaseLabel label) {
        this.label = label;
    }

    public User getLabeledBy() {
        return labeledBy;
    }

    public void setLabeledBy(User labeledBy) {
        this.labeledBy = labeledBy;
    }

    public Instant getLabeledAt() {
        return labeledAt;
    }

    public void setLabeledAt(Instant labeledAt) {
        this.labeledAt = labeledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
