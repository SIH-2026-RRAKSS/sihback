package com.sih.dataservice.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class PredictionResponse {

    @JsonProperty("risk")
    private double risk;

    @JsonProperty("confidence")
    private double confidence;

    @JsonProperty("top_nodes")
    private List<TopNodeExplanation> topNodes = new ArrayList<>();

    @JsonProperty("top_features")
    private List<TopFeatureExplanation> topFeatures = new ArrayList<>();

    @JsonProperty("model_version")
    private String modelVersion;

    public PredictionResponse() {
    }

    public PredictionResponse(double risk, double confidence, List<TopNodeExplanation> topNodes, List<TopFeatureExplanation> topFeatures, String modelVersion) {
        this.risk = risk;
        this.confidence = confidence;
        this.topNodes = topNodes != null ? topNodes : new ArrayList<>();
        this.topFeatures = topFeatures != null ? topFeatures : new ArrayList<>();
        this.modelVersion = modelVersion;
    }

    public double getRisk() {
        return risk;
    }

    public void setRisk(double risk) {
        this.risk = risk;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<TopNodeExplanation> getTopNodes() {
        return topNodes;
    }

    public void setTopNodes(List<TopNodeExplanation> topNodes) {
        this.topNodes = topNodes;
    }

    public List<TopFeatureExplanation> getTopFeatures() {
        return topFeatures;
    }

    public void setTopFeatures(List<TopFeatureExplanation> topFeatures) {
        this.topFeatures = topFeatures;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
