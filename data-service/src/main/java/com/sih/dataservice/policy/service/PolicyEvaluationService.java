package com.sih.dataservice.policy.service;

import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.ml.entity.Prediction;
import com.sih.dataservice.ml.repository.PredictionRepository;
import com.sih.dataservice.policy.dto.PolicyEvaluationResultDto;
import com.sih.dataservice.policy.dto.ThresholdEvaluationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PolicyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationService.class);

    private final ComplaintRepository complaintRepository;
    private final PredictionRepository predictionRepository;

    public PolicyEvaluationService(ComplaintRepository complaintRepository,
                                   PredictionRepository predictionRepository) {
        this.complaintRepository = complaintRepository;
        this.predictionRepository = predictionRepository;
    }

    private static class LabeledPrediction {
        final boolean actualFraud;
        final double risk;

        LabeledPrediction(boolean actualFraud, double risk) {
            this.actualFraud = actualFraud;
            this.risk = risk;
        }
    }

    @Transactional(readOnly = true)
    public PolicyEvaluationResultDto evaluateThresholdSweep() {
        List<Complaint> closedComplaints = complaintRepository.findByStatusIn(List.of(
                ComplaintStatus.CLOSED_FRAUD,
                ComplaintStatus.CLOSED_NOT_FRAUD
        ));

        List<LabeledPrediction> samples = new ArrayList<>();
        for (Complaint complaint : closedComplaints) {
            Optional<Prediction> latestPrediction = predictionRepository
                    .findTopByComplaintIdOrderByCreatedAtDesc(complaint.getId());

            if (latestPrediction.isPresent()) {
                boolean actualFraud = complaint.getStatus() == ComplaintStatus.CLOSED_FRAUD;
                double risk = latestPrediction.get().getRisk().doubleValue();
                samples.add(new LabeledPrediction(actualFraud, risk));
            }
        }

        List<ThresholdEvaluationPoint> points = new ArrayList<>();
        double optimalThreshold = 0.50;
        double maxF1 = 0.0;

        // Sweep from 0.10 to 0.90 in increments of 0.05
        for (int i = 10; i <= 90; i += 5) {
            double threshold = i / 100.0;

            long tp = 0;
            long fp = 0;
            long tn = 0;
            long fn = 0;

            for (LabeledPrediction sample : samples) {
                boolean predictedPositive = sample.risk >= threshold;
                if (sample.actualFraud && predictedPositive) {
                    tp++;
                } else if (!sample.actualFraud && predictedPositive) {
                    fp++;
                } else if (!sample.actualFraud && !predictedPositive) {
                    tn++;
                } else {
                    fn++;
                }
            }

            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            double f1 = (precision + recall) > 0 ? 2 * (precision * recall) / (precision + recall) : 0.0;

            precision = round(precision, 4);
            recall = round(recall, 4);
            f1 = round(f1, 4);

            points.add(new ThresholdEvaluationPoint(
                    threshold, tp, fp, tn, fn, precision, recall, f1));

            if (f1 > maxF1) {
                maxF1 = f1;
                optimalThreshold = threshold;
            }
        }

        return new PolicyEvaluationResultDto(samples.size(), optimalThreshold, maxF1, points);
    }

    private double round(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
