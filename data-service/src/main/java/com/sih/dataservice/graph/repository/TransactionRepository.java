package com.sih.dataservice.graph.repository;

import com.sih.dataservice.graph.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByUtr(String utr);

    boolean existsByUtr(String utr);

    List<Transaction> findBySenderIdAndTimestampBetween(UUID senderId, Instant start, Instant end);

    List<Transaction> findByReceiverIdAndTimestampBetween(UUID receiverId, Instant start, Instant end);

    @Query("SELECT t FROM Transaction t WHERE (t.sender.id = :entityId OR t.receiver.id = :entityId) AND t.timestamp BETWEEN :start AND :end")
    List<Transaction> findByEntityIdAndTimestampBetween(@Param("entityId") UUID entityId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT t FROM Transaction t WHERE t.timestamp >= :since ORDER BY t.timestamp ASC")
    List<Transaction> findTransactionsSince(@Param("since") Instant since);

    Page<Transaction> findByComplaintId(UUID complaintId, Pageable pageable);

    List<Transaction> findByComplaintId(UUID complaintId);

    @Query("SELECT t FROM Transaction t WHERE t.sender.id IN :entityIds OR t.receiver.id IN :entityIds")
    List<Transaction> findBySenderIdInOrReceiverIdIn(@Param("entityIds") java.util.Collection<UUID> entityIds);

    Page<Transaction> findByUploadId(UUID uploadId, Pageable pageable);
}
