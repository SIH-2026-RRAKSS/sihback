package com.sih.dataservice.stats.service;

import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.graph.repository.TransactionRepository;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.repository.ModelVersionRepository;
import com.sih.dataservice.ml.repository.PredictionRepository;
import com.sih.dataservice.stats.dto.SystemStatsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private ModelVersionRepository modelVersionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(complaintRepository, predictionRepository, modelVersionRepository, transactionRepository);
    }

    @Test
    @DisplayName("getOverviewStats computes counts, sums, and model availability rate accurately")
    void testGetOverviewStats() {
        when(complaintRepository.count()).thenReturn(25L);
        when(complaintRepository.sumTotalAmount()).thenReturn(BigDecimal.valueOf(1500000.50));

        List<Object[]> statusGroups = List.of(
                new Object[]{ComplaintStatus.FILED, 10L},
                new Object[]{ComplaintStatus.ASSIGNED, 5L},
                new Object[]{ComplaintStatus.CLOSED_FRAUD, 10L}
        );
        when(complaintRepository.countByStatusGroup()).thenReturn(statusGroups);

        when(predictionRepository.count()).thenReturn(20L);
        when(predictionRepository.countByModelAvailableTrue()).thenReturn(18L);

        ModelVersion mv = new ModelVersion("graphsage", "v1.0.0", "{}", ModelVersionStatus.ACTIVE);
        when(modelVersionRepository.findByNameAndStatus("graphsage", ModelVersionStatus.ACTIVE))
                .thenReturn(Optional.of(mv));

        when(transactionRepository.count()).thenReturn(150L);

        SystemStatsDto stats = statsService.getOverviewStats();

        assertThat(stats.getTotalComplaints()).isEqualTo(25L);
        assertThat(stats.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500000.50));
        assertThat(stats.getComplaintsByStatus().get(ComplaintStatus.FILED.name())).isEqualTo(10L);
        assertThat(stats.getComplaintsByStatus().get(ComplaintStatus.ASSIGNED.name())).isEqualTo(5L);
        assertThat(stats.getComplaintsByStatus().get(ComplaintStatus.CLOSED_FRAUD.name())).isEqualTo(10L);
        assertThat(stats.getTotalPredictions()).isEqualTo(20L);
        assertThat(stats.getModelAvailablePredictions()).isEqualTo(18L);
        assertThat(stats.getModelAvailabilityRate()).isEqualTo(0.90);
        assertThat(stats.getActiveModelVersion()).isEqualTo("graphsage:v1.0.0");
        assertThat(stats.getTotalTransactions()).isEqualTo(150L);
    }
}
