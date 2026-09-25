package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.CaseLabel;
import jakarta.validation.constraints.NotNull;

public class UpdateCaseLabelRequest {

    @NotNull
    private CaseLabel label;

    private String note;

    public UpdateCaseLabelRequest() {
    }

    public UpdateCaseLabelRequest(CaseLabel label, String note) {
        this.label = label;
        this.note = note;
    }

    public CaseLabel getLabel() {
        return label;
    }

    public void setLabel(CaseLabel label) {
        this.label = label;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReason() {
        return note;
    }

    public void setReason(String reason) {
        this.note = reason;
    }
}
