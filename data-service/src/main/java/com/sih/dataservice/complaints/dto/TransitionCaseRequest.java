package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.CaseLabel;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import jakarta.validation.constraints.NotNull;

public class TransitionCaseRequest {

    @NotNull
    private ComplaintStatus toStatus;

    private String note;

    private CaseLabel label;

    public TransitionCaseRequest() {
    }

    public TransitionCaseRequest(ComplaintStatus toStatus, String note, CaseLabel label) {
        this.toStatus = toStatus;
        this.note = note;
        this.label = label;
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

    public CaseLabel getLabel() {
        return label;
    }

    public void setLabel(CaseLabel label) {
        this.label = label;
    }
}
