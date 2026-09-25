package com.sih.dataservice.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class PredictionRequest {

    @JsonProperty("model_name")
    private String modelName = "graphsage";

    @JsonProperty("nodes")
    private List<PredictionNodeDto> nodes = new ArrayList<>();

    @JsonProperty("edges")
    private List<PredictionEdgeDto> edges = new ArrayList<>();

    public PredictionRequest() {
    }

    public PredictionRequest(String modelName, List<PredictionNodeDto> nodes, List<PredictionEdgeDto> edges) {
        this.modelName = modelName;
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.edges = edges != null ? edges : new ArrayList<>();
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public List<PredictionNodeDto> getNodes() {
        return nodes;
    }

    public void setNodes(List<PredictionNodeDto> nodes) {
        this.nodes = nodes;
    }

    public List<PredictionEdgeDto> getEdges() {
        return edges;
    }

    public void setEdges(List<PredictionEdgeDto> edges) {
        this.edges = edges;
    }
}
