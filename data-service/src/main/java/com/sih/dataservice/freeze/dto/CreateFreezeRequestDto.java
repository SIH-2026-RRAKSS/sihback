package com.sih.dataservice.freeze.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class CreateFreezeRequestDto {

    @NotNull(message = "complaintId is required")
    private UUID complaintId;

    @NotNull(message = "entityId is required")
    private UUID entityId;

    private String note;

    public CreateFreezeRequestDto() {
    }

    public CreateFreezeRequestDto(UUID complaintId, UUID entityId, String note) {
        this.complaintId = complaintId;
        this.entityId = entityId;
        this.note = note;
    }

    public UUID getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(UUID complaintId) {
        this.complaintId = complaintId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
