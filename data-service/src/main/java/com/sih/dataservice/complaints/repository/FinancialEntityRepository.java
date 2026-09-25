package com.sih.dataservice.complaints.repository;

import com.sih.dataservice.complaints.entity.FinancialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialEntityRepository extends JpaRepository<FinancialEntity, UUID> {
    Optional<FinancialEntity> findByAccountHash(String accountHash);
}
