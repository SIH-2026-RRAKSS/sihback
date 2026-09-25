package com.sih.dataservice.complaints.service;

import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.dto.EvidenceDto;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.Evidence;
import com.sih.dataservice.complaints.repository.EvidenceRepository;
import com.sih.dataservice.users.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenceStorageService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceStorageService.class);

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB limit (FR-CMP-4)
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final EvidenceRepository evidenceRepository;
    private final Path evidenceStorageDir;

    public EvidenceStorageService(
            EvidenceRepository evidenceRepository,
            @Value("${storage.evidence.path:../storage/evidence}") String storageEvidencePath) {
        this.evidenceRepository = evidenceRepository;
        this.evidenceStorageDir = Paths.get(storageEvidencePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.evidenceStorageDir);
        } catch (IOException e) {
            log.error("Could not initialize evidence storage directory at {}", this.evidenceStorageDir, e);
        }
    }

    /**
     * Validates, stores evidence file to disk, computes SHA-256, and records entity.
     */
    @Transactional
    public EvidenceDto storeEvidence(Complaint complaint, User uploader, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Evidence file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw ApiException.badRequest("Evidence file size exceeds 10MB maximum limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw ApiException.badRequest("Unsupported file type: " + contentType + ". Allowed: JPEG, PNG, WEBP, PDF");
        }

        try {
            // Compute SHA-256 checksum
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = file.getBytes();
            byte[] hash = digest.digest(fileBytes);
            String sha256Hex = HexFormat.of().formatHex(hash);

            // Create target directory per complaint
            Path complaintDir = evidenceStorageDir.resolve(complaint.getId().toString());
            Files.createDirectories(complaintDir);

            // Target filename: sanitized sha256 + original filename
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "evidence";
            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedFileName = sha256Hex.substring(0, 16) + "_" + safeFilename;
            Path destination = complaintDir.resolve(storedFileName).normalize();

            // Guard against path traversal
            if (!destination.startsWith(complaintDir)) {
                throw ApiException.badRequest("Invalid file path");
            }

            Files.write(destination, fileBytes);

            Evidence evidence = new Evidence(
                    complaint,
                    destination.toString(),
                    contentType,
                    file.getSize(),
                    sha256Hex,
                    uploader
            );
            Evidence saved = evidenceRepository.save(evidence);
            log.info("Stored evidence id={} for complaint id={} (size={}, sha256={})",
                    saved.getId(), complaint.getId(), saved.getSizeBytes(), sha256Hex);

            return EvidenceDto.fromEntity(saved);

        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to process and store evidence file", e);
            throw new IllegalStateException("Failed to process evidence file", e);
        }
    }

    @Transactional(readOnly = true)
    public List<EvidenceDto> getEvidenceForComplaint(UUID complaintId) {
        return evidenceRepository.findByComplaintId(complaintId)
                .stream()
                .map(EvidenceDto::fromEntity)
                .toList();
    }
}
