package com.sih.dataservice.ml.client;

import com.sih.dataservice.ml.dto.PredictionEdgeDto;
import com.sih.dataservice.ml.dto.PredictionNodeDto;
import com.sih.dataservice.ml.dto.PredictionRequest;
import com.sih.dataservice.ml.dto.PredictionResponse;
import com.sih.dataservice.ml.dto.TopFeatureExplanation;
import com.sih.dataservice.ml.dto.TopNodeExplanation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("fakeModelClient")
public class FakeModelClient implements ModelClient {

    private boolean available = true;

    @Override
    public PredictionResponse predict(PredictionRequest request) {
        if (!available) {
            throw new IllegalStateException("Model service is currently unavailable");
        }

        int nodeCount = request.getNodes() != null ? request.getNodes().size() : 0;
        int edgeCount = request.getEdges() != null ? request.getEdges().size() : 0;

        // Deterministic baseline heuristic for testing
        double baseRisk = nodeCount > 2 ? 0.85 : 0.40;
        double confidence = Math.min(0.95, 0.70 + nodeCount * 0.05);

        List<TopNodeExplanation> topNodes = new ArrayList<>();
        if (request.getNodes() != null) {
            for (int i = 0; i < Math.min(3, request.getNodes().size()); i++) {
                PredictionNodeDto node = request.getNodes().get(i);
                topNodes.add(new TopNodeExplanation(node.getId(), Math.max(0.1, baseRisk - i * 0.15)));
            }
        }

        List<TopFeatureExplanation> topFeatures = List.of(
                new TopFeatureExplanation("in_out_degree_ratio", 0.45),
                new TopFeatureExplanation("amount_deviation", 0.35),
                new TopFeatureExplanation("velocity_burst", 0.20)
        );

        return new PredictionResponse(
                baseRisk,
                confidence,
                topNodes,
                topFeatures,
                "graphsage-v1.0.0-fake"
        );
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
