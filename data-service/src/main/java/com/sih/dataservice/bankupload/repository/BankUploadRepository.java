package com.sih.dataservice.bankupload.repository;

import com.sih.dataservice.bankupload.entity.BankUpload;
import com.sih.dataservice.bankupload.entity.BankUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankUploadRepository extends JpaRepository<BankUpload, UUID> {

    Optional<BankUpload> findByFileChecksum(String fileChecksum);

    boolean existsByFileChecksum(String fileChecksum);

    Page<BankUpload> findByBankId(UUID bankId, Pageable pageable);

    Page<BankUpload> findByBankIdAndStatus(UUID bankId, BankUploadStatus status, Pageable pageable);
}
