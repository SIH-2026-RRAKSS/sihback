package com.sih.dataservice.policy.service;

import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.entity.Prediction;
import com.sih.dataservice.ml.repository.PredictionRepository;
import com.sih.dataservice.policy.dto.PolicyEvaluationResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyEvaluationServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private PredictionRepository predictionRepository;

    private PolicyEvaluationService policyEvaluationService;

    @BeforeEach
    void setUp() {
        policyEvaluationService = new PolicyEvaluationService(complaintRepository, predictionRepository);
    }

    @Test
    @DisplayName("evaluateThresholdSweep correctly evaluates precision, recall, and finds optimal threshold")
    void testThresholdSweep_WithLabeledData() {
        ModelVersion mv = new ModelVersion("graphsage", "v1.0.0", "{}", ModelVersionStatus.ACTIVE);

        Complaint fraud1 = new Complaint();
        fraud1.setId(UUID.randomUUID());
        fraud1.setStatus(ComplaintStatus.CLOSED_FRAUD);
        Prediction p1 = new Prediction(fraud1, mv, BigDecimal.valueOf(0.8500), BigDecimal.valueOf(0.9), "{}", true);

        Complaint fraud2 = new Complaint();
        fraud2.setId(UUID.randomUUID());
        fraud2.setStatus(ComplaintStatus.CLOSED_FRAUD);
        Prediction p2 = new Prediction(fraud2, mv, BigDecimal.valueOf(0.6500), BigDecimal.valueOf(0.9), "{}", true);

        Complaint notFraud1 = new Complaint();
        notFraud1.setId(UUID.randomUUID());
        notFraud1.setStatus(ComplaintStatus.CLOSED_NOT_FRAUD);
        Prediction p3 = new Prediction(notFraud1, mv, BigDecimal.valueOf(0.2000), BigDecimal.valueOf(0.9), "{}", true);

        when(complaintRepository.findByStatusIn(any())).thenReturn(List.of(fraud1, fraud2, notFraud1));
        when(predictionRepository.findTopByComplaintIdOrderByCreatedAtDesc(fraud1.getId())).thenReturn(Optional.of(p1));
        when(predictionRepository.findTopByComplaintIdOrderByCreatedAtDesc(fraud2.getId())).thenReturn(Optional.of(p2));
        when(predictionRepository.findTopByComplaintIdOrderByCreatedAtDesc(notFraud1.getId())).thenReturn(Optional.of(p3));

        PolicyEvaluationResultDto result = policyEvaluationService.evaluateThresholdSweep();

        assertThat(result.getTotalEvaluatedCases()).isEqualTo(3);
        assertThat(result.getPoints()).isNotEmpty();
        assertThat(result.getMaxF1Score()).isGreaterThan(0.0);
        // At threshold 0.50: TP=2, FP=0, TN=1, FN=0 -> Precision=1.0, Recall=1.0, F1=1.0
        assertThat(result.getMaxF1Score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("evaluateThresholdSweep returns clean baseline when no labeled complaints exist")
    void testThresholdSweep_Empty() {
        when(complaintRepository.findByStatusIn(any())).thenReturn(List.of());

        PolicyEvaluationResultDto result = policyEvaluationService.evaluateThresholdSweep();

        assertThat(result.getTotalEvaluatedCases()).isEqualTo(0);
        assertThat(result.getPoints()).hasSize(17); // 0.10 to 0.90 step 0.05
        assertThat(result.getMaxF1Score()).isEqualTo(0.0);
    }
}
