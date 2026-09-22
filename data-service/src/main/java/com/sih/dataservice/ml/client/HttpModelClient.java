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
            @Value("${model.service.url:http://localhost:8001}") String modelServiceUrl,
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
            return restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PredictionResponse.class);
        } catch (Exception e) {
            log.warn("Model service call failed to {}: {}", modelServiceUrl, e.getMessage());
            throw new IllegalStateException("Model service call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String res = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(String.class);
            return res != null && res.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }
}
