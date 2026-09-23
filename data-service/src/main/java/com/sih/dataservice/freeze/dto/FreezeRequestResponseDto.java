package com.sih.dataservice.freeze.dto;

import com.sih.dataservice.freeze.entity.FreezeRequest;
import com.sih.dataservice.freeze.entity.FreezeRequestStatus;

import java.time.Instant;
import java.util.UUID;

public class FreezeRequestResponseDto {

    private UUID id;
    private UUID complaintId;
    private String complaintReference;
    private UUID entityId;
    private String accountHash;
    private String entityType;
    private UUID bankId;
    private String bankName;
    private String bankCode;
    private UUID raisedBy;
    private String raisedByName;
    private Instant raisedAt;
    private Instant dueAt;
    private FreezeRequestStatus status;
    private String responseNote;
    private UUID respondedBy;
    private String respondedByName;
    private Instant respondedAt;
    private Instant reminderSentAt;
    private Instant escalatedAt;
    private boolean isOverdue;
    private Instant createdAt;

    public FreezeRequestResponseDto() {
    }

    public static FreezeRequestResponseDto fromEntity(FreezeRequest f, Instant now) {
        FreezeRequestResponseDto dto = new FreezeRequestResponseDto();
        dto.setId(f.getId());
        dto.setComplaintId(f.getComplaint().getId());
        dto.setComplaintReference(f.getComplaint().getHumanReference());
        dto.setEntityId(f.getEntity().getId());
        dto.setAccountHash(f.getEntity().getAccountHash());
        dto.setEntityType(f.getEntity().getType() != null ? f.getEntity().getType().name() : null);
        dto.setBankId(f.getBank().getId());
        dto.setBankName(f.getBank().getName());
        dto.setBankCode(f.getBank().getCode());
        dto.setRaisedBy(f.getRaisedBy().getId());
        dto.setRaisedByName(f.getRaisedBy().getName());
        dto.setRaisedAt(f.getRaisedAt());
        dto.setDueAt(f.getDueAt());
        dto.setStatus(f.getStatus());
        dto.setResponseNote(f.getResponseNote());
        if (f.getRespondedBy() != null) {
            dto.setRespondedBy(f.getRespondedBy().getId());
            dto.setRespondedByName(f.getRespondedBy().getName());
        }
        dto.setRespondedAt(f.getRespondedAt());
        dto.setReminderSentAt(f.getReminderSentAt());
        dto.setEscalatedAt(f.getEscalatedAt());
        dto.setOverdue(f.getStatus() == FreezeRequestStatus.OPEN && now.isAfter(f.getDueAt()));
        dto.setCreatedAt(f.getCreatedAt());
        return dto;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(UUID complaintId) {
        this.complaintId = complaintId;
    }

    public String getComplaintReference() {
        return complaintReference;
    }

    public void setComplaintReference(String complaintReference) {
        this.complaintReference = complaintReference;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getAccountHash() {
        return accountHash;
    }

    public void setAccountHash(String accountHash) {
        this.accountHash = accountHash;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getBankId() {
        return bankId;
    }

    public void setBankId(UUID bankId) {
        this.bankId = bankId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public UUID getRaisedBy() {
        return raisedBy;
    }

    public void setRaisedBy(UUID raisedBy) {
        this.raisedBy = raisedBy;
    }

    public String getRaisedByName() {
        return raisedByName;
    }

    public void setRaisedByName(String raisedByName) {
        this.raisedByName = raisedByName;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public void setRaisedAt(Instant raisedAt) {
        this.raisedAt = raisedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public FreezeRequestStatus getStatus() {
        return status;
    }

    public void setStatus(FreezeRequestStatus status) {
        this.status = status;
    }

    public String getResponseNote() {
        return responseNote;
    }

    public void setResponseNote(String responseNote) {
        this.responseNote = responseNote;
    }

    public UUID getRespondedBy() {
        return respondedBy;
    }

    public void setRespondedBy(UUID respondedBy) {
        this.respondedBy = respondedBy;
    }

    public String getRespondedByName() {
        return respondedByName;
    }

    public void setRespondedByName(String respondedByName) {
        this.respondedByName = respondedByName;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(Instant reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(Instant escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public boolean isOverdue() {
        return isOverdue;
    }

    public void setOverdue(boolean overdue) {
        isOverdue = overdue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
