package com.sih.dataservice.bankupload.dto;

import com.sih.dataservice.bankupload.entity.BankUpload;
import com.sih.dataservice.bankupload.entity.BankUploadStatus;

import java.time.Instant;
import java.util.UUID;

public class BankUploadResponseDto {

    private UUID id;
    private UUID bankId;
    private String bankName;
    private UUID uploaderId;
    private String uploaderName;
    private String fileChecksum;
    private int rowCount;
    private BankUploadStatus status;
    private UUID reviewerId;
    private String reviewerName;
    private String reviewNote;
    private Instant reviewedAt;
    private Instant createdAt;

    public BankUploadResponseDto() {
    }

    public static BankUploadResponseDto fromEntity(BankUpload upload) {
        BankUploadResponseDto dto = new BankUploadResponseDto();
        dto.setId(upload.getId());
        if (upload.getBank() != null) {
            dto.setBankId(upload.getBank().getId());
            dto.setBankName(upload.getBank().getName());
        }
        if (upload.getUploader() != null) {
            dto.setUploaderId(upload.getUploader().getId());
            dto.setUploaderName(upload.getUploader().getName());
        }
        dto.setFileChecksum(upload.getFileChecksum());
        dto.setRowCount(upload.getRowCount());
        dto.setStatus(upload.getStatus());
        if (upload.getReviewer() != null) {
            dto.setReviewerId(upload.getReviewer().getId());
            dto.setReviewerName(upload.getReviewer().getName());
        }
        dto.setReviewNote(upload.getReviewNote());
        dto.setReviewedAt(upload.getReviewedAt());
        dto.setCreatedAt(upload.getCreatedAt());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public UUID getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(UUID uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public BankUploadStatus getStatus() {
        return status;
    }

    public void setStatus(BankUploadStatus status) {
        this.status = status;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(UUID reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
