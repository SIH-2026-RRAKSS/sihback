package com.sih.dataservice.ml.client;

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

        double baseRisk = 0.85;
        double confidence = 0.90;

        List<TopNodeExplanation> topNodes = new ArrayList<>();
        topNodes.add(new TopNodeExplanation(request.getSeedEntityId() != null ? request.getSeedEntityId() : "C_001", baseRisk));
        topNodes.add(new TopNodeExplanation("C_002", 0.70));
        topNodes.add(new TopNodeExplanation("C_003", 0.55));

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
