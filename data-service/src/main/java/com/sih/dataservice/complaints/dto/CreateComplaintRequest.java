package com.sih.dataservice.complaints.dto;

import com.sih.dataservice.complaints.entity.ComplaintChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreateComplaintRequest {

    @NotBlank
    private String fraudType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotNull
    private Instant incidentTime;

    @NotBlank
    private String descriptionOriginal;

    private String descriptionLanguage = "en";

    private ComplaintChannel channel = ComplaintChannel.WEB;

    private UUID jurisdictionId;
    private String district;

    @Valid
    private List<AccountRequestDto> accounts = new ArrayList<>();

    public CreateComplaintRequest() {
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

    public ComplaintChannel getChannel() {
        return channel;
    }

    public void setChannel(ComplaintChannel channel) {
        this.channel = channel;
    }

    public UUID getJurisdictionId() {
        return jurisdictionId;
    }

    public void setJurisdictionId(UUID jurisdictionId) {
        this.jurisdictionId = jurisdictionId;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public List<AccountRequestDto> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<AccountRequestDto> accounts) {
        this.accounts = accounts;
    }
}
