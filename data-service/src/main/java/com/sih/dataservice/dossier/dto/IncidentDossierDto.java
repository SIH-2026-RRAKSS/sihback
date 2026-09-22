package com.sih.dataservice.dossier.dto;

import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.ml.dto.PredictionDto;

import java.time.Instant;

public class IncidentDossierDto {
    private IncidentDetailDto incident;
    private PredictionDto prediction;
    private Instant generatedAt;
    private String generatedBy;
    private String classificationNotice;

    public IncidentDossierDto() {
    }

    public IncidentDossierDto(IncidentDetailDto incident, PredictionDto prediction,
                              Instant generatedAt, String generatedBy, String classificationNotice) {
        this.incident = incident;
        this.prediction = prediction;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
        this.classificationNotice = classificationNotice;
    }

    public IncidentDetailDto getIncident() {
        return incident;
    }

    public void setIncident(IncidentDetailDto incident) {
        this.incident = incident;
    }

    public PredictionDto getPrediction() {
        return prediction;
    }

    public void setPrediction(PredictionDto prediction) {
        this.prediction = prediction;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public String getClassificationNotice() {
        return classificationNotice;
    }

    public void setClassificationNotice(String classificationNotice) {
        this.classificationNotice = classificationNotice;
    }
}
