package com.sih.dataservice.complaints.repository;

import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {

    Optional<Complaint> findByHumanReference(String humanReference);

    Page<Complaint> findByComplainantId(UUID complainantId, Pageable pageable);

    Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);

    @Query("SELECT c FROM Complaint c WHERE c.jurisdiction.path LIKE CONCAT(:pathPrefix, '%')")
    Page<Complaint> findByJurisdictionPathPrefix(@Param("pathPrefix") String pathPrefix, Pageable pageable);

    @Query("SELECT c FROM Complaint c JOIN ComplaintAccount ca ON ca.complaint.id = c.id WHERE ca.entity.bank.id = :bankId")
    Page<Complaint> findByInvolvedBankId(@Param("bankId") UUID bankId, Pageable pageable);

    long countByCreatedAtBetween(Instant start, Instant end);
}
