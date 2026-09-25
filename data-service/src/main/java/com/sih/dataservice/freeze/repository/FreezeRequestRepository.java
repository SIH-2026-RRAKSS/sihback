package com.sih.dataservice.freeze.repository;

import com.sih.dataservice.freeze.entity.FreezeRequest;
import com.sih.dataservice.freeze.entity.FreezeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FreezeRequestRepository extends JpaRepository<FreezeRequest, UUID> {

    Page<FreezeRequest> findByBankId(UUID bankId, Pageable pageable);

    Page<FreezeRequest> findByBankIdAndStatus(UUID bankId, FreezeRequestStatus status, Pageable pageable);

    List<FreezeRequest> findByComplaintId(UUID complaintId);

    @Query("SELECT f FROM FreezeRequest f WHERE f.complaint.id IN :complaintIds")
    Page<FreezeRequest> findByComplaintIdIn(@Param("complaintIds") List<UUID> complaintIds, Pageable pageable);

    @Query("SELECT f FROM FreezeRequest f WHERE f.status = 'OPEN' AND f.reminderSentAt IS NULL AND f.raisedAt <= :cutoffTime")
    List<FreezeRequest> findPendingReminders(@Param("cutoffTime") Instant cutoffTime);

    @Query("SELECT f FROM FreezeRequest f WHERE f.status = 'OPEN' AND f.escalatedAt IS NULL AND f.dueAt <= :currentTime")
    List<FreezeRequest> findOverdueEscalations(@Param("currentTime") Instant currentTime);
}
