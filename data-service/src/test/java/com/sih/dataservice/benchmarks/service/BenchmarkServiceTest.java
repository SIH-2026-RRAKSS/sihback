package com.sih.dataservice.benchmarks.service;

import com.sih.dataservice.benchmarks.dto.StreamingBenchmarkResult;
import com.sih.dataservice.benchmarks.dto.ThreeWayBenchmarkResult;
import com.sih.dataservice.datasets.SyntheticDataGenerator;
import com.sih.dataservice.gate.service.AnomalyGateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkServiceTest {

    private AnomalyGateService anomalyGateService;
    private SyntheticDataGenerator syntheticDataGenerator;
    private BenchmarkService benchmarkService;

    @BeforeEach
    void setUp() {
        anomalyGateService = new AnomalyGateService(3.0, 5, 60, 5, 50000.0, 3);
        syntheticDataGenerator = new SyntheticDataGenerator();
        benchmarkService = new BenchmarkService(anomalyGateService, syntheticDataGenerator);
    }

    @Test
    @DisplayName("runThreeWayBenchmark produces comparative evaluation across Gate, XGBoost, and GraphSAGE (FR-BEN-1)")
    void testRunThreeWayBenchmark() {
        ThreeWayBenchmarkResult result = benchmarkService.runThreeWayBenchmark("synthetic_benchmark_seed42");

        assertThat(result).isNotNull();
        assertThat(result.getDatasetName()).isEqualTo("synthetic_benchmark_seed42");
        assertThat(result.getTotalTransactions()).isGreaterThan(50);
        assertThat(result.getMuleTransactionsCount()).isGreaterThan(0);

        // Gate-only checks
        assertThat(result.getGateOnly()).isNotNull();
        assertThat(result.getGateOnly().getModelName()).isEqualTo("anomaly-gate-heuristic");
        assertThat(result.getGateOnly().getF1Score()).isGreaterThan(0.0);
        assertThat(result.getGateOnly().getThroughputTxPerSec()).isGreaterThan(0.0);

        // XGBoost checks
        assertThat(result.getXgboost()).isNotNull();
        assertThat(result.getXgboost().getModelName()).isEqualTo("xgboost-v1.0.0");
        assertThat(result.getXgboost().getPrecision()).isGreaterThan(0.7);
        assertThat(result.getXgboost().getF1Score()).isGreaterThan(0.7);

        // GraphSAGE checks
        assertThat(result.getGraphSage()).isNotNull();
        assertThat(result.getGraphSage().getModelName()).isEqualTo("graphsage-v1.0.0");
        assertThat(result.getGraphSage().getRecall()).isGreaterThan(0.8);
        assertThat(result.getGraphSage().getF1Score()).isGreaterThan(0.8);

        // Reduction percentage
        assertThat(result.getGateReductionPercentage()).isGreaterThan(40.0);
    }

    @Test
    @DisplayName("runStreamingBenchmark calculates velocity throughput and Stage 2 reduction ratio")
    void testRunStreamingBenchmark() {
        StreamingBenchmarkResult result = benchmarkService.runStreamingBenchmark(100);

        assertThat(result).isNotNull();
        assertThat(result.getTotalTransactions()).isGreaterThan(0);
        assertThat(result.getThroughputTxPerSec()).isGreaterThan(0.0);
        assertThat(result.getAvgGateLatencyMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.getStage2ReductionRatio()).isBetween(0.0, 1.0);
    }
}
