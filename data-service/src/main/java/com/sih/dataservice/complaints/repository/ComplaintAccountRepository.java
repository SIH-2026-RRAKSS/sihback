package com.sih.dataservice.complaints.repository;

import com.sih.dataservice.complaints.entity.ComplaintAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplaintAccountRepository extends JpaRepository<ComplaintAccount, UUID> {
    List<ComplaintAccount> findByComplaintId(UUID complaintId);
}
