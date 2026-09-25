package com.sih.dataservice.ml.repository;

import com.sih.dataservice.ml.entity.Prediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, UUID> {

    Optional<Prediction> findTopByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

    List<Prediction> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

    Page<Prediction> findAll(Pageable pageable);

    long countByModelAvailableTrue();
}
