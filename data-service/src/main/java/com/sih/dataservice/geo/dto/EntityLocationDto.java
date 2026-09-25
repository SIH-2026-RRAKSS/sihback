package com.sih.dataservice.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EntityLocationDto {

    @JsonProperty("entity_id")
    private String entityId;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("holder_name")
    private String holderName;

    @JsonProperty("city")
    private String city;

    @JsonProperty("state")
    private String state;

    @JsonProperty("latitude")
    private double latitude;

    @JsonProperty("longitude")
    private double longitude;

    @JsonProperty("risk_probability")
    private double riskProbability;

    @JsonProperty("confidence_tier")
    private String confidenceTier;

    @JsonProperty("flagged_amount")
    private double flaggedAmount;

    public EntityLocationDto() {
    }

    public EntityLocationDto(String entityId, String entityType, String holderName, String city, String state,
                             double latitude, double longitude, double riskProbability, String confidenceTier,
                             double flaggedAmount) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.holderName = holderName;
        this.city = city;
        this.state = state;
        this.latitude = latitude;
        this.longitude = longitude;
        this.riskProbability = riskProbability;
        this.confidenceTier = confidenceTier;
        this.flaggedAmount = flaggedAmount;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getRiskProbability() {
        return riskProbability;
    }

    public void setRiskProbability(double riskProbability) {
        this.riskProbability = riskProbability;
    }

    public String getConfidenceTier() {
        return confidenceTier;
    }

    public void setConfidenceTier(String confidenceTier) {
        this.confidenceTier = confidenceTier;
    }

    public double getFlaggedAmount() {
        return flaggedAmount;
    }

    public void setFlaggedAmount(double flaggedAmount) {
        this.flaggedAmount = flaggedAmount;
    }
}
