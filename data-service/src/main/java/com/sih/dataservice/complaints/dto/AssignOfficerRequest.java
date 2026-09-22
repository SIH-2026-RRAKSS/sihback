package com.sih.dataservice.complaints.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AssignOfficerRequest {

    @NotNull
    private UUID officerId;

    public AssignOfficerRequest() {
    }

    public AssignOfficerRequest(UUID officerId) {
        this.officerId = officerId;
    }

    public UUID getOfficerId() {
        return officerId;
    }

    public void setOfficerId(UUID officerId) {
        this.officerId = officerId;
    }
}
