package com.sih.dataservice.ml.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@RestController
public class ModelProxyController {

    private static final Logger log = LoggerFactory.getLogger(ModelProxyController.class);
    private final RestClient restClient;

    public ModelProxyController(
            @Value("${model.service.url:http://localhost:8000}") String modelServiceUrl,
            @Value("${model.service.timeout.ms:5000}") int timeoutMs) {
        
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(modelServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // Replaces StatsController mapped to /stats
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<?> proxyStats() {
        return proxyGet("/api/stats");
    }

    @GetMapping(value = "/incidents/{id}/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<?> proxyIncidentGraph(@PathVariable("id") String id) {
        return proxyGet("/api/incidents/" + id + "/graph");
    }

    @GetMapping(value = "/geo/corridors", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<?> proxyGeoCorridors() {
        return proxyGet("/api/geo/corridors");
    }

    private ResponseEntity<?> proxyGet(String path) {
        try {
            String rawJson = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok(rawJson);
        } catch (Exception e) {
            log.error("Failed to proxy GET {} to model service: {}", path, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\": \"Model service unavailable or returned an error\"}");
        }
    }
}
