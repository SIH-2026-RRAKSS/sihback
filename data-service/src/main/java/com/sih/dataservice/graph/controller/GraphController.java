package com.sih.dataservice.graph.controller;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.dto.ApiResponse;
import com.sih.dataservice.graph.dto.SubgraphResponseDto;
import com.sih.dataservice.graph.service.GraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/graph")
@Tag(name = "Graph Analytics", description = "Endpoints for temporal transaction graph exploration and K-hop extraction")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @Operation(summary = "Extract a bounded K-hop transaction subgraph around an entity with caller scope masking")
    @GetMapping("/khop")
    @PreAuthorize("hasAnyRole('POLICE', 'CYBER_OFFICER', 'BANK_EMPLOYEE', 'BANK_MANAGER')")
    public ResponseEntity<ApiResponse<SubgraphResponseDto>> getKhopSubgraph(
            @RequestParam("entityId") UUID entityId,
            @RequestParam(name = "hops", defaultValue = "2") int hops,
            @RequestParam(name = "startTime", required = false) Instant startTime,
            @RequestParam(name = "endTime", required = false) Instant endTime,
            @RequestParam(name = "maxNodes", defaultValue = "50") int maxNodes,
            @AuthenticationPrincipal UserPrincipal principal) {

        SubgraphResponseDto response = graphService.getKhopSubgraph(
                entityId, hops, startTime, endTime, maxNodes, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
