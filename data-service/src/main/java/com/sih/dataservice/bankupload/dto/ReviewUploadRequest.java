package com.sih.dataservice.bankupload.dto;

import com.sih.dataservice.bankupload.entity.BankUploadStatus;
import jakarta.validation.constraints.NotNull;

public class ReviewUploadRequest {

    @NotNull
    private BankUploadStatus status;

    private String reviewNote;

    public ReviewUploadRequest() {
    }

    public ReviewUploadRequest(BankUploadStatus status, String reviewNote) {
        this.status = status;
        this.reviewNote = reviewNote;
    }

    public BankUploadStatus getStatus() {
        return status;
    }

    public void setStatus(BankUploadStatus status) {
        this.status = status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }
}
