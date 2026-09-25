package com.sih.dataservice.ml.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.RequestParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping(value = "/incidents", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER', 'ADMIN')")
    public ResponseEntity<?> proxyListIncidents(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "tier", required = false) String tier,
            @RequestParam(name = "min_risk", required = false) Double minRisk) {
        
        int pythonPage = page + 1; // Translate 0-indexed to 1-indexed for Python
        
        StringBuilder path = new StringBuilder("/api/incidents?page=").append(pythonPage).append("&page_size=").append(size);
        if (tier != null) path.append("&tier=").append(tier);
        if (minRisk != null) path.append("&min_risk=").append(minRisk);
        
        return proxyGet(path.toString());
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping(value = "/incidents/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER', 'ADMIN')")
    public ResponseEntity<?> proxyIncidentDetail(@PathVariable("id") String id) {
        try {
            String rawJson = restClient.get()
                    .uri("/api/incidents/" + id)
                    .retrieve()
                    .body(String.class);
            
            ObjectNode rootNode = (ObjectNode) objectMapper.readTree(rawJson);
            ObjectNode explainability = objectMapper.createObjectNode();
            
            if (rootNode.has("investigative_evidence_bullets")) {
                explainability.set("investigative_evidence_bullets", rootNode.get("investigative_evidence_bullets"));
                rootNode.remove("investigative_evidence_bullets");
            }
            
            if (rootNode.has("top_terminal_details")) {
                explainability.set("terminal_prediction", rootNode.get("top_terminal_details"));
                rootNode.remove("top_terminal_details");
            }
            
            rootNode.set("explainability", explainability);
            
            return ResponseEntity.ok(rootNode.toString());
        } catch (Exception e) {
            log.error("Failed to proxy detail for {}, error: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\": \"Model service unavailable or returned an error\"}");
        }
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






