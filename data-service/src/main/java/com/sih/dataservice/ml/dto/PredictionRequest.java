package com.sih.dataservice.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class PredictionRequest {

    @JsonProperty("seed_entity_id")
    private String seedEntityId;

    public PredictionRequest() {
    }

    public PredictionRequest(String seedEntityId) {
        this.seedEntityId = seedEntityId;
    }

    public String getSeedEntityId() {
        return seedEntityId;
    }

    public void setSeedEntityId(String seedEntityId) {
        this.seedEntityId = seedEntityId;
    }
}
