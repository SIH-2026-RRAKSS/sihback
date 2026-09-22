package com.sih.dataservice.bankupload.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.bankupload.dto.BankUploadResponseDto;
import com.sih.dataservice.bankupload.dto.ReviewUploadRequest;
import com.sih.dataservice.bankupload.entity.BankUpload;
import com.sih.dataservice.bankupload.entity.BankUploadStatus;
import com.sih.dataservice.bankupload.repository.BankUploadRepository;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.complaints.entity.FinancialEntity;
import com.sih.dataservice.complaints.repository.FinancialEntityRepository;
import com.sih.dataservice.graph.engine.TemporalGraphEngine;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.entity.TransactionSource;
import com.sih.dataservice.graph.repository.TransactionRepository;
import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class BankUploadService {

    private static final Logger log = LoggerFactory.getLogger(BankUploadService.class);

    private final BankUploadRepository bankUploadRepository;
    private final TransactionRepository transactionRepository;
    private final FinancialEntityRepository financialEntityRepository;
    private final BankRepository bankRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final TemporalGraphEngine graphEngine;
    private final AuditService auditService;
    private final Clock clock;
    private final Path storageDir;

    public BankUploadService(
            BankUploadRepository bankUploadRepository,
            TransactionRepository transactionRepository,
            FinancialEntityRepository financialEntityRepository,
            BankRepository bankRepository,
            UserRepository userRepository,
            CryptoService cryptoService,
            TemporalGraphEngine graphEngine,
            AuditService auditService,
            Clock clock,
            @Value("${storage.bankuploads.path:../storage/bank_uploads}") String storagePath) {
        this.bankUploadRepository = bankUploadRepository;
        this.transactionRepository = transactionRepository;
        this.financialEntityRepository = financialEntityRepository;
        this.bankRepository = bankRepository;
        this.userRepository = userRepository;
        this.cryptoService = cryptoService;
        this.graphEngine = graphEngine;
        this.auditService = auditService;
        this.clock = clock;
        this.storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            log.error("Could not create bank uploads storage directory", e);
        }
    }

    /**
     * Uploads transaction file (CSV) as PENDING (FR-UPL-1).
     * File is not ingested into graph until approved.
     */
    @Transactional
    public BankUploadResponseDto uploadTransactions(MultipartFile file, UserPrincipal principal, String clientIp) {
        if (principal.getRole() != UserRole.BANK_EMPLOYEE && principal.getRole() != UserRole.BANK_MANAGER) {
            throw ApiException.forbidden("Only bank staff can upload transaction files");
        }

        if (principal.getBankId() == null) {
            throw ApiException.badRequest("Caller is not associated with any bank");
        }

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Upload file cannot be empty");
        }

        Bank bank = bankRepository.findById(principal.getBankId())
                .orElseThrow(() -> ApiException.badRequest("Bank record not found"));

        User uploader = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("Uploader user record not found"));

        try {
            byte[] fileBytes = file.getBytes();

            // Compute SHA-256 checksum for idempotency and duplicate rejection (FR-UPL-3)
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fileBytes);
            String checksum = HexFormat.of().formatHex(hash);

            if (bankUploadRepository.existsByFileChecksum(checksum)) {
                throw ApiException.badRequest("Duplicate file: An upload with identical checksum already exists");
            }

            // Parse and count rows to validate structural integrity
            int rowCount = validateCsvStructure(fileBytes);

            // Persist file safely to disk
            Path bankSubdir = storageDir.resolve(bank.getId().toString());
            Files.createDirectories(bankSubdir);
            Path destination = bankSubdir.resolve(checksum + ".csv").normalize();
            if (!destination.startsWith(bankSubdir)) {
                throw ApiException.badRequest("Invalid file path");
            }
            Files.write(destination, fileBytes);

            BankUpload upload = new BankUpload(bank, uploader, destination.toString(), checksum, rowCount);
            BankUpload saved = bankUploadRepository.save(upload);

            auditService.log(uploader.getId(), principal.getRole().name(), "UPLOAD_BANK_TRANSACTIONS",
                    "BANK_UPLOAD", saved.getId().toString(),
                    String.format("{\"bankId\":\"%s\",\"rowCount\":%d,\"checksum\":\"%s\"}", bank.getId(), rowCount, checksum),
                    clientIp);

            log.info("Bank upload created id={} by user={} for bank={} (rows={})",
                    saved.getId(), uploader.getId(), bank.getName(), rowCount);

            return BankUploadResponseDto.fromEntity(saved);

        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to process bank upload file", e);
            throw new IllegalStateException("Failed to process bank upload file", e);
        }
    }

    /**
     * Reviews and approves/rejects upload with two-person rule (FR-UPL-2, FR-UPL-3).
     * Reviewer must be a manager of the same bank, and CANNOT be the uploader.
     */
    @Transactional
    public BankUploadResponseDto reviewUpload(UUID uploadId, ReviewUploadRequest request, UserPrincipal principal, String clientIp) {
        BankUpload upload = bankUploadRepository.findById(uploadId)
                .orElseThrow(() -> ApiException.notFound("Bank upload not found"));

        if (upload.getStatus() != BankUploadStatus.PENDING) {
            throw ApiException.badRequest("Upload has already been reviewed with status: " + upload.getStatus());
        }

        // Two-person rule: uploader can never review their own upload (FR-UPL-2, DB check constraint)
        if (upload.getUploader().getId().equals(principal.getId())) {
            throw ApiException.badRequest("Two-person approval violation: Uploader cannot review their own upload");
        }

        // Role authorization: must be BANK_MANAGER of the same bank or CYBER_OFFICER
        if (principal.getRole() == UserRole.BANK_MANAGER) {
            if (!upload.getBank().getId().equals(principal.getBankId())) {
                throw ApiException.forbidden("Bank manager can only review uploads for their own bank");
            }
        } else if (principal.getRole() != UserRole.CYBER_OFFICER) {
            throw ApiException.forbidden("Only BANK_MANAGER or CYBER_OFFICER can review uploads");
        }

        User reviewer = userRepository.findById(principal.getId()).orElse(null);
        Instant now = Instant.now(clock);

        upload.setStatus(request.getStatus());
        upload.setReviewer(reviewer);
        upload.setReviewNote(request.getReviewNote());
        upload.setReviewedAt(now);

        if (request.getStatus() == BankUploadStatus.APPROVED) {
            // Ingest transactions and update in-memory graph (FR-UPL-3, FR-GRA-1)
            List<Transaction> ingestedTransactions = ingestApprovedFile(upload);
            upload.setRowCount(ingestedTransactions.size());
            bankUploadRepository.save(upload);

            auditService.log(principal.getId(), principal.getRole().name(), "APPROVE_BANK_UPLOAD",
                    "BANK_UPLOAD", upload.getId().toString(),
                    String.format("{\"ingestedCount\":%d}", ingestedTransactions.size()), clientIp);

            log.info("Approved bank upload id={}, ingested {} transactions into graph",
                    upload.getId(), ingestedTransactions.size());
        } else {
            bankUploadRepository.save(upload);

            auditService.log(principal.getId(), principal.getRole().name(), "REJECT_BANK_UPLOAD",
                    "BANK_UPLOAD", upload.getId().toString(),
                    String.format("{\"note\":\"%s\"}", request.getReviewNote() != null ? request.getReviewNote() : ""),
                    clientIp);

            log.info("Rejected bank upload id={} with note={}", upload.getId(), request.getReviewNote());
        }

        return BankUploadResponseDto.fromEntity(upload);
    }

    @Transactional(readOnly = true)
    public Page<BankUploadResponseDto> listUploads(UserPrincipal principal, Pageable pageable) {
        if (principal.getRole() == UserRole.BANK_EMPLOYEE || principal.getRole() == UserRole.BANK_MANAGER) {
            if (principal.getBankId() == null) {
                return Page.empty(pageable);
            }
            return bankUploadRepository.findByBankId(principal.getBankId(), pageable)
                    .map(BankUploadResponseDto::fromEntity);
        } else if (principal.getRole() == UserRole.CYBER_OFFICER) {
            return bankUploadRepository.findAll(pageable)
                    .map(BankUploadResponseDto::fromEntity);
        } else {
            throw ApiException.forbidden("Access denied to bank uploads");
        }
    }

    private int validateCsvStructure(byte[] fileBytes) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw ApiException.badRequest("CSV file is empty");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = line.split(",");
                if (tokens.length < 5) {
                    throw ApiException.badRequest("Invalid CSV row format (expected at least 5 columns: utr,sender,receiver,amount,timestamp): " + line);
                }
                // Verify amount
                try {
                    BigDecimal amt = new BigDecimal(tokens[3].trim());
                    if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                        throw ApiException.badRequest("Transaction amount must be positive: " + tokens[3]);
                    }
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest("Invalid amount format in row: " + tokens[3]);
                }
                count++;
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Could not parse CSV content: " + e.getMessage());
        }
        return count;
    }

    private List<Transaction> ingestApprovedFile(BankUpload upload) {
        List<Transaction> transactionsToSave = new ArrayList<>();
        Path filePath = Paths.get(upload.getFilePath());

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = line.split(",");
                String utr = tokens[0].trim();
                String senderAccount = tokens[1].trim();
                String receiverAccount = tokens[2].trim();
                BigDecimal amount = new BigDecimal(tokens[3].trim());
                Instant timestamp = parseTimestamp(tokens[4].trim());

                FinancialEntity sender = getOrCreateEntity(senderAccount, upload.getBank(), EntityType.ACCOUNT);
                FinancialEntity receiver = getOrCreateEntity(receiverAccount, null, EntityType.ACCOUNT);

                if (!transactionRepository.existsByUtr(utr)) {
                    Transaction tx = new Transaction(utr, sender, receiver, amount, timestamp, TransactionSource.BANK_UPLOAD);
                    tx.setUpload(upload);
                    transactionsToSave.add(tx);
                }
            }

            List<Transaction> saved = transactionRepository.saveAll(transactionsToSave);
            // Ingest directly into in-memory temporal graph engine
            graphEngine.addTransactions(saved);
            return saved;

        } catch (IOException e) {
            log.error("Failed to read approved upload file for ingestion", e);
            throw new IllegalStateException("Failed to read file during ingestion", e);
        }
    }

    private FinancialEntity getOrCreateEntity(String rawAccount, Bank bank, EntityType type) {
        String hash = cryptoService.computeHmac(rawAccount);
        String encrypted = cryptoService.encrypt(rawAccount);

        return financialEntityRepository.findByAccountHash(hash)
                .orElseGet(() -> financialEntityRepository.save(new FinancialEntity(
                        hash, encrypted, bank, type
                )));
    }

    private Instant parseTimestamp(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            try {
                long epoch = Long.parseLong(raw);
                return Instant.ofEpochMilli(epoch);
            } catch (Exception ex) {
                return Instant.now(clock);
            }
        }
    }
}
