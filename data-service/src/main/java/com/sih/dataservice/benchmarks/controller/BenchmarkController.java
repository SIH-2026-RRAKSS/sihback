package com.sih.dataservice.benchmarks.controller;

import com.sih.dataservice.benchmarks.dto.StreamingBenchmarkResult;
import com.sih.dataservice.benchmarks.dto.ThreeWayBenchmarkResult;
import com.sih.dataservice.benchmarks.service.BenchmarkService;
import com.sih.dataservice.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Benchmarks", description = "Performance and accuracy benchmarks across models and datasets (FR-BEN-1)")
@RestController
@RequestMapping("/benchmarks")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Operation(summary = "Execute 3-way evaluation comparing Gate-Only, XGBoost, and GraphSAGE (FR-BEN-1)")
    @GetMapping({"/three-way", "/three_way"})
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN', 'POLICE')")
    public ResponseEntity<ApiResponse<ThreeWayBenchmarkResult>> getThreeWayBenchmark(
            @RequestParam(name = "dataset", defaultValue = "synthetic_benchmark_seed42") String dataset) {
        ThreeWayBenchmarkResult result = benchmarkService.runThreeWayBenchmark(dataset);
        return ResponseEntity.ok(ApiResponse.ok(result, "Three-way benchmark completed successfully"));
    }

    @Operation(summary = "Execute streaming velocity throughput and gate reduction benchmark (FR-BEN-1)")
    @GetMapping({"/streaming", "/benchmark"})
    @PreAuthorize("hasAnyRole('CYBER_OFFICER', 'ADMIN', 'POLICE')")
    public ResponseEntity<ApiResponse<StreamingBenchmarkResult>> getStreamingBenchmark(
            @RequestParam(name = "count", defaultValue = "200") int count) {
        StreamingBenchmarkResult result = benchmarkService.runStreamingBenchmark(count);
        return ResponseEntity.ok(ApiResponse.ok(result, "Streaming velocity benchmark completed successfully"));
    }
}
