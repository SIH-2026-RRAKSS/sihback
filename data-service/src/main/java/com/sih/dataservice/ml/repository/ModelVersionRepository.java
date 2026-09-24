package com.sih.dataservice.ml.repository;

import com.sih.dataservice.ml.entity.ModelVersion;
import com.sih.dataservice.ml.entity.ModelVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {

    Optional<ModelVersion> findByNameAndStatus(String name, ModelVersionStatus status);

    List<ModelVersion> findByName(String name);

    List<ModelVersion> findByStatus(ModelVersionStatus status);
    List<ModelVersion> findAllByOrderByCreatedAtDesc();
}
