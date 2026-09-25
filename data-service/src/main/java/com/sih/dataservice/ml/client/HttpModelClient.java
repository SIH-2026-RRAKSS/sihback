package com.sih.dataservice.ml.client;

import com.sih.dataservice.ml.dto.PredictionRequest;
import com.sih.dataservice.ml.dto.PredictionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
@Primary
public class HttpModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(HttpModelClient.class);

    private final RestClient restClient;
    private final String modelServiceUrl;

    public HttpModelClient(
            @Value("${model.service.url:http://localhost:8000}") String modelServiceUrl,
            @Value("${model.service.timeout.ms:3000}") int timeoutMs) {
        this.modelServiceUrl = modelServiceUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(modelServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PredictionResponse predict(PredictionRequest request) {
        try {
            com.fasterxml.jackson.databind.JsonNode rawResponse = restClient.post()
                    .uri("/api/predict/subgraph")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(com.fasterxml.jackson.databind.JsonNode.class);

            if (rawResponse == null) {
                throw new IllegalStateException("Empty response from model service");
            }

            double risk = rawResponse.path("risk_probability").asDouble(0.5);
            String confidenceTier = rawResponse.path("confidence_tier").asText("NORMAL");
            double confidence = switch (confidenceTier) {
                case "HIGH_CONFIDENCE" -> 0.90;
                case "MEDIUM_CONFIDENCE" -> 0.60;
                case "FIRST_TIME_RING_CANDIDATE" -> 0.75;
                case "NORMAL" -> 0.30;
                default -> 0.50;
            };

            java.util.List<com.sih.dataservice.ml.dto.TopNodeExplanation> topNodes = new java.util.ArrayList<>();
            com.fasterxml.jackson.databind.JsonNode terminals = rawResponse.path("terminals");
            if (terminals.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode term : terminals) {
                    String id = term.path("terminal_id").asText("unknown");
                    double score = term.path("terminal_score").asDouble(0.5);
                    topNodes.add(new com.sih.dataservice.ml.dto.TopNodeExplanation(id, score));
                }
            }

            String modelVersion = "graphsage-live";

            return new PredictionResponse(risk, confidence, topNodes, new java.util.ArrayList<>(), modelVersion);
        } catch (Exception e) {
            log.warn("Model service call failed to {}: {}", modelServiceUrl, e.getMessage());
            throw new IllegalStateException("Model service call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String res = restClient.get()
                    .uri("/api/health")
                    .retrieve()
                    .body(String.class);
            return res != null && res.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }
}
