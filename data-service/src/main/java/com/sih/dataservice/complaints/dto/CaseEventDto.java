package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.CaseEvent;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import java.time.Instant;
import java.util.UUID;

public class CaseEventDto {

    private UUID id;
    private UUID actorId;
    private String actorName;
    private ComplaintStatus fromStatus;
    private ComplaintStatus toStatus;
    private String note;
    private Instant createdAt;

    public CaseEventDto() {
    }

    public static CaseEventDto fromEntity(CaseEvent event) {
        CaseEventDto dto = new CaseEventDto();
        dto.setId(event.getId());
        if (event.getActor() != null) {
            dto.setActorId(event.getActor().getId());
            dto.setActorName(event.getActor().getName());
        }
        dto.setFromStatus(event.getFromStatus());
        dto.setToStatus(event.getToStatus());
        dto.setNote(event.getNote());
        dto.setCreatedAt(event.getCreatedAt());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public ComplaintStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ComplaintStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ComplaintStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ComplaintStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
