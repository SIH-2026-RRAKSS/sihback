package com.sih.dataservice.ml.repository;

import com.sih.dataservice.ml.entity.TrainingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainingSnapshotRepository extends JpaRepository<TrainingSnapshot, UUID> {
    Optional<TrainingSnapshot> findTopByOrderByVersionDesc();
    Optional<TrainingSnapshot> findByVersion(int version);
    List<TrainingSnapshot> findAllByOrderByVersionDesc();
}
