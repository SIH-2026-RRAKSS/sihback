package com.sih.dataservice.complaints.repository;

import com.sih.dataservice.complaints.entity.CaseEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseEventRepository extends JpaRepository<CaseEvent, UUID> {
    List<CaseEvent> findByComplaintIdOrderByCreatedAtAsc(UUID complaintId);
}
