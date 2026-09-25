package com.sih.dataservice.stats.controller;

import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.stats.dto.SystemStatsDto;
import com.sih.dataservice.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats/system")
@Tag(name = "Statistics", description = "System metrics and operational analytics")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "Get overview statistics of complaints, predictions, amounts, and system health")
    @GetMapping({"", "/overview"})
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SystemStatsDto>> getOverviewStats() {
        SystemStatsDto stats = statsService.getOverviewStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
