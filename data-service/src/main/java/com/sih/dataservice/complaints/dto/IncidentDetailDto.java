package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.CaseLabel;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintChannel;
import com.sih.dataservice.complaints.entity.ComplaintStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IncidentDetailDto {

    private UUID id;
    private String humanReference;
    private UUID complainantId;
    private UUID jurisdictionId;
    private String jurisdictionName;
    private String jurisdictionPath;
    private String fraudType;
    private BigDecimal amount;
    private Instant incidentTime;
    private String descriptionOriginal;
    private String descriptionLanguage;
    private String descriptionEnglish;
    private ComplaintChannel channel;
    private ComplaintStatus status;
    private UUID assignedOfficerId;
    private String assignedOfficerName;
    private CaseLabel label;
    private UUID labeledById;
    private Instant labeledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ComplaintAccountDto> accounts = new ArrayList<>();
    private List<CaseEventDto> events = new ArrayList<>();
    private List<EvidenceDto> evidence = new ArrayList<>();

    // ML/Prediction Fields
    private Double riskScore;
    private String confidenceTier;
    private String modelVersion;
    private String topTerminalId; // We map the first terminal here for the summary view
    private String topTerminalCity;
    private List<String> topNodes = new ArrayList<>();

    public IncidentDetailDto() {
    }

    public static IncidentDetailDto fromEntity(Complaint complaint) {
        IncidentDetailDto dto = new IncidentDetailDto();
        dto.setId(complaint.getId());
        dto.setHumanReference(complaint.getHumanReference());
        if (complaint.getComplainant() != null) {
            dto.setComplainantId(complaint.getComplainant().getId());
        }
        if (complaint.getJurisdiction() != null) {
            dto.setJurisdictionId(complaint.getJurisdiction().getId());
            dto.setJurisdictionName(complaint.getJurisdiction().getName());
            dto.setJurisdictionPath(complaint.getJurisdiction().getPath());
        }
        dto.setFraudType(complaint.getFraudType());
        dto.setAmount(complaint.getAmount());
        dto.setIncidentTime(complaint.getIncidentTime());
        dto.setDescriptionOriginal(complaint.getDescriptionOriginal());
        dto.setDescriptionLanguage(complaint.getDescriptionLanguage());
        dto.setDescriptionEnglish(complaint.getDescriptionEnglish());
        dto.setChannel(complaint.getChannel());
        dto.setStatus(complaint.getStatus());
        if (complaint.getAssignedOfficer() != null) {
            dto.setAssignedOfficerId(complaint.getAssignedOfficer().getId());
            dto.setAssignedOfficerName(complaint.getAssignedOfficer().getName());
        }
        dto.setLabel(complaint.getLabel());
        if (complaint.getLabeledBy() != null) {
            dto.setLabeledById(complaint.getLabeledBy().getId());
        }
        dto.setLabeledAt(complaint.getLabeledAt());
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

    public UUID getComplainantId() {
        return complainantId;
    }

    public void setComplainantId(UUID complainantId) {
        this.complainantId = complainantId;
    }

    public UUID getJurisdictionId() {
        return jurisdictionId;
    }

    public void setJurisdictionId(UUID jurisdictionId) {
        this.jurisdictionId = jurisdictionId;
    }

    public String getJurisdictionName() {
        return jurisdictionName;
    }

    public void setJurisdictionName(String jurisdictionName) {
        this.jurisdictionName = jurisdictionName;
    }

    public String getJurisdictionPath() {
        return jurisdictionPath;
    }

    public void setJurisdictionPath(String jurisdictionPath) {
        this.jurisdictionPath = jurisdictionPath;
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

    public String getDescriptionLanguage() {
        return descriptionLanguage;
    }

    public void setDescriptionLanguage(String descriptionLanguage) {
        this.descriptionLanguage = descriptionLanguage;
    }

    public String getDescriptionEnglish() {
        return descriptionEnglish;
    }

    public void setDescriptionEnglish(String descriptionEnglish) {
        this.descriptionEnglish = descriptionEnglish;
    }

    public ComplaintChannel getChannel() {
        return channel;
    }

    public void setChannel(ComplaintChannel channel) {
        this.channel = channel;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public UUID getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public void setAssignedOfficerId(UUID assignedOfficerId) {
        this.assignedOfficerId = assignedOfficerId;
    }

    public String getAssignedOfficerName() {
        return assignedOfficerName;
    }

    public void setAssignedOfficerName(String assignedOfficerName) {
        this.assignedOfficerName = assignedOfficerName;
    }

    public CaseLabel getLabel() {
        return label;
    }

    public void setLabel(CaseLabel label) {
        this.label = label;
    }

    public UUID getLabeledById() {
        return labeledById;
    }

    public void setLabeledById(UUID labeledById) {
        this.labeledById = labeledById;
    }

    public Instant getLabeledAt() {
        return labeledAt;
    }

    public void setLabeledAt(Instant labeledAt) {
        this.labeledAt = labeledAt;
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

    public List<ComplaintAccountDto> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<ComplaintAccountDto> accounts) {
        this.accounts = accounts;
    }

    public List<CaseEventDto> getEvents() {
        return events;
    }

    public void setEvents(List<CaseEventDto> events) {
        this.events = events;
    }

    public List<EvidenceDto> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<EvidenceDto> evidence) {
        this.evidence = evidence;
    }
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    public String getConfidenceTier() { return confidenceTier; }
    public void setConfidenceTier(String confidenceTier) { this.confidenceTier = confidenceTier; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getTopTerminalId() { return topTerminalId; }
    public void setTopTerminalId(String topTerminalId) { this.topTerminalId = topTerminalId; }
    public String getTopTerminalCity() { return topTerminalCity; }
    public void setTopTerminalCity(String topTerminalCity) { this.topTerminalCity = topTerminalCity; }
    public List<String> getTopNodes() { return topNodes; }
    public void setTopNodes(List<String> topNodes) { this.topNodes = topNodes; }
}
