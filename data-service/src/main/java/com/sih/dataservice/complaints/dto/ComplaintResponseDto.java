package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintChannel;
import com.sih.dataservice.complaints.entity.PublicStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ComplaintResponseDto {

    private UUID id;
    private String humanReference;
    private String fraudType;
    private BigDecimal amount;
    private Instant incidentTime;
    private String descriptionOriginal;
    private PublicStatus status; // Complainants only ever see simplified 4-state status (FR-CMP-2)
    private ComplaintChannel channel;
    private Instant createdAt;
    private Instant updatedAt;

    public ComplaintResponseDto() {
    }

    public static ComplaintResponseDto fromEntity(Complaint complaint) {
        ComplaintResponseDto dto = new ComplaintResponseDto();
        dto.setId(complaint.getId());
        dto.setHumanReference(complaint.getHumanReference());
        dto.setFraudType(complaint.getFraudType());
        dto.setAmount(complaint.getAmount());
        dto.setIncidentTime(complaint.getIncidentTime());
        dto.setDescriptionOriginal(complaint.getDescriptionOriginal());
        dto.setStatus(PublicStatus.fromInternalStatus(complaint.getStatus()));
        dto.setChannel(complaint.getChannel());
        dto.setCreatedAt(complaint.getCreatedAt());
        dto.setUpdatedAt(complaint.getUpdatedAt());
        return dto;
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

    public PublicStatus getStatus() {
        return status;
    }

    public void setStatus(PublicStatus status) {
        this.status = status;
    }

    public ComplaintChannel getChannel() {
        return channel;
    }

    public void setChannel(ComplaintChannel channel) {
        this.channel = channel;
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
