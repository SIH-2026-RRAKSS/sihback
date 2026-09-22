package com.sih.dataservice.ml.dto;

import java.util.HashMap;
import java.util.Map;

public class PredictionNodeDto {

    private String id;
    private Map<String, Double> features = new HashMap<>();

    public PredictionNodeDto() {
    }

    public PredictionNodeDto(String id, Map<String, Double> features) {
        this.id = id;
        this.features = features != null ? features : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Double> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Double> features) {
        this.features = features;
    }
}
