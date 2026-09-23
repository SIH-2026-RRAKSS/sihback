package com.sih.dataservice.freeze.dto;

import com.sih.dataservice.freeze.entity.FreezeRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RespondFreezeRequestDto {

    @NotNull(message = "status is required")
    private FreezeRequestStatus status;

    @NotBlank(message = "responseNote is required")
    private String responseNote;

    public RespondFreezeRequestDto() {
    }

    public RespondFreezeRequestDto(FreezeRequestStatus status, String responseNote) {
        this.status = status;
        this.responseNote = responseNote;
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
}
