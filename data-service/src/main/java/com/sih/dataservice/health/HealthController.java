package com.sih.dataservice.health;

import com.sih.dataservice.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "Health & Status", description = "System health, version and uptime information")
public class HealthController {

    private final Clock clock;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${spring.application.name:data-service}")
    private String applicationName;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @Operation(summary = "Detailed health and service metadata")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("service", applicationName);
        healthInfo.put("profile", activeProfile);
        healthInfo.put("timestamp", Instant.now(clock).toString());
        healthInfo.put("version", "1.0.0");

        return ResponseEntity.ok(ApiResponse.ok(healthInfo));
    }

    @Operation(summary = "Ping check")
    @GetMapping("/ping")
    public ResponseEntity<ApiResponse<String>> ping() {
        return ResponseEntity.ok(ApiResponse.ok("pong"));
    }
}
