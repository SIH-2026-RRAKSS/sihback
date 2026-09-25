package com.sih.dataservice.stats.service;

import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.graph.repository.TransactionRepository;
import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import com.sih.dataservice.ml.repository.ModelVersionRepository;
import com.sih.dataservice.ml.repository.PredictionRepository;
import com.sih.dataservice.stats.dto.SystemStatsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StatsService {

    private final ComplaintRepository complaintRepository;
    private final PredictionRepository predictionRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final TransactionRepository transactionRepository;

    public StatsService(ComplaintRepository complaintRepository,
                        PredictionRepository predictionRepository,
                        ModelVersionRepository modelVersionRepository,
                        TransactionRepository transactionRepository) {
        this.complaintRepository = complaintRepository;
        this.predictionRepository = predictionRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public SystemStatsDto getOverviewStats() {
        long totalComplaints = complaintRepository.count();
        BigDecimal totalAmount = complaintRepository.sumTotalAmount();

        Map<String, Long> statusCounts = new HashMap<>();
        for (ComplaintStatus status : ComplaintStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }

        List<Object[]> statusGroups = complaintRepository.countByStatusGroup();
        for (Object[] row : statusGroups) {
            if (row[0] != null) {
                statusCounts.put(row[0].toString(), (Long) row[1]);
            }
        }

        long totalPredictions = predictionRepository.count();
        long availablePredictions = predictionRepository.countByModelAvailableTrue();
        double availabilityRate = totalPredictions > 0
                ? BigDecimal.valueOf((double) availablePredictions / totalPredictions)
                    .setScale(4, RoundingMode.HALF_UP).doubleValue()
                : 1.0;

        Optional<ModelVersion> activeMv = modelVersionRepository.findByNameAndStatus("graphsage", ModelVersionStatus.ACTIVE);
        String activeVersion = activeMv.map(mv -> mv.getName() + ":" + mv.getVersion()).orElse("none");

        long totalTransactions = transactionRepository.count();

        return new SystemStatsDto(
                totalComplaints,
                statusCounts,
                totalAmount != null ? totalAmount : BigDecimal.ZERO,
                totalPredictions,
                availablePredictions,
                availabilityRate,
                activeVersion,
                totalTransactions
        );
    }
}
