package com.sih.dataservice.ml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintAccount;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.ComplaintAccountRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.graph.entity.Transaction;
import com.sih.dataservice.graph.repository.TransactionRepository;
import com.sih.dataservice.ml.entity.TrainingSnapshot;
import com.sih.dataservice.ml.repository.TrainingSnapshotRepository;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Service for generating immutable, de-identified training snapshots (FR-RTR-2, NFR-SEC-4, NFR-PRV-1).
 */
@Service
public class SnapshotExportService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotExportService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final TrainingSnapshotRepository snapshotRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintAccountRepository complaintAccountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Path snapshotsDir;

    public SnapshotExportService(
            TrainingSnapshotRepository snapshotRepository,
            ComplaintRepository complaintRepository,
            ComplaintAccountRepository complaintAccountRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${storage.snapshots.path:../storage/snapshots}") String storageSnapshotsPath) {
        this.snapshotRepository = snapshotRepository;
        this.complaintRepository = complaintRepository;
        this.complaintAccountRepository = complaintAccountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.snapshotsDir = Paths.get(storageSnapshotsPath);
    }

    /**
     * Generates a new de-identified training snapshot from all currently closed cases (FR-RTR-1, FR-RTR-2).
     * Strictly restricted to CYBER_OFFICER or ADMIN.
     */
    @Transactional
    public TrainingSnapshot exportSnapshot(UserPrincipal principal, String clientIp) {
        if (principal.getRole() != UserRole.CYBER_OFFICER && principal.getRole() != UserRole.ADMIN) {
            throw ApiException.forbidden("Only CYBER_OFFICER or ADMIN can export training snapshots");
        }

        User creator = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        int nextVersion = snapshotRepository.findTopByOrderByVersionDesc()
                .map(s -> s.getVersion() + 1)
                .orElse(1);

        List<Complaint> closedCases = complaintRepository.findByStatusIn(List.of(
                ComplaintStatus.CLOSED_FRAUD,
                ComplaintStatus.CLOSED_NOT_FRAUD
        ));

        if (closedCases.isEmpty()) {
            throw ApiException.badRequest("No labeled closed cases available for snapshot export");
        }

        // Generate per-snapshot salt (random 32 bytes hex)
        String salt = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        int fraudCount = 0;
        List<Map<String, Object>> deidentifiedGraphs = new ArrayList<>();

        for (Complaint complaint : closedCases) {
            int label = complaint.getStatus() == ComplaintStatus.CLOSED_FRAUD ? 1 : 0;
            if (label == 1) {
                fraudCount++;
            }

            Map<String, Object> graph = buildDeidentifiedGraph(complaint, label, salt);
            deidentifiedGraphs.add(graph);
        }

        try {
            Files.createDirectories(snapshotsDir);
            String fileName = String.format("snapshot-v%d.json", nextVersion);
            Path filePath = snapshotsDir.resolve(fileName);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile(), StandardCharsets.UTF_8))) {
                // Header Manifest
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("schema_version", "1.0");
                manifest.put("version", nextVersion);
                manifest.put("case_count", closedCases.size());
                manifest.put("fraud_count", fraudCount);
                manifest.put("created_at", Instant.now().toString());
                manifest.put("salt_hash", sha256Hex(salt));
                writer.write(objectMapper.writeValueAsString(manifest));
                writer.newLine();

                // Graph rows
                for (Map<String, Object> graph : deidentifiedGraphs) {
                    writer.write(objectMapper.writeValueAsString(graph));
                    writer.newLine();
                }
            }

            // Compute SHA-256 checksum of generated file
            byte[] fileBytes = Files.readAllBytes(filePath);
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fileBytes));

            TrainingSnapshot snapshot = new TrainingSnapshot(
                    nextVersion,
                    creator,
                    closedCases.size(),
                    fraudCount,
                    filePath.toAbsolutePath().toString(),
                    checksum
            );

            TrainingSnapshot saved = snapshotRepository.save(snapshot);

            // Audit log
            String detailJson = String.format("{\"version\":%d,\"caseCount\":%d,\"fraudCount\":%d,\"checksum\":\"%s\"}",
                    nextVersion, closedCases.size(), fraudCount, checksum);
            auditService.log(principal.getId(), principal.getRole().name(), "CREATE_SNAPSHOT",
                    "TRAINING_SNAPSHOT", saved.getId().toString(), detailJson, clientIp);

            log.info("Created training snapshot v{} with {} cases ({} fraud) at {}",
                    nextVersion, closedCases.size(), fraudCount, filePath);

            return saved;

        } catch (Exception e) {
            log.error("Failed to export training snapshot v{}: {}", nextVersion, e.getMessage(), e);
            throw new IllegalStateException("Failed to export training snapshot: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildDeidentifiedGraph(Complaint complaint, int label, String salt) {
        Map<String, Object> graph = new LinkedHashMap<>();
        String complaintHashedId = rehashId(complaint.getId().toString(), salt);
        graph.put("complaint_id", complaintHashedId);
        graph.put("label", label);

        List<ComplaintAccount> accounts = complaintAccountRepository.findByComplaintId(complaint.getId());
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        String rootNodeId = null;

        for (ComplaintAccount ca : accounts) {
            String rawHash = ca.getEntity() != null ? ca.getEntity().getAccountHash() : UUID.randomUUID().toString();
            String deidentifiedNodeId = rehashId(rawHash, salt);

            if (rootNodeId == null) {
                rootNodeId = deidentifiedNodeId;
            }

            if (nodeIds.add(deidentifiedNodeId)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", deidentifiedNodeId);
                node.put("role", ca.getRole() != null ? ca.getRole().name() : "SUSPECT");
                node.put("entity_type", ca.getEntity() != null && ca.getEntity().getType() != null
                        ? ca.getEntity().getType().name() : "ACCOUNT");
                nodes.add(node);
            }
        }

        // Transactions / Edges
        List<Map<String, Object>> edges = new ArrayList<>();
        if (!accounts.isEmpty()) {
            List<UUID> entityIds = accounts.stream()
                    .map(ca -> ca.getEntity() != null ? ca.getEntity().getId() : null)
                    .filter(Objects::nonNull)
                    .toList();

            List<Transaction> txList = transactionRepository.findBySenderIdInOrReceiverIdIn(entityIds);
            for (Transaction tx : txList) {
                String sourceRaw = tx.getSender() != null ? tx.getSender().getAccountHash() : null;
                String targetRaw = tx.getReceiver() != null ? tx.getReceiver().getAccountHash() : null;

                if (sourceRaw != null && targetRaw != null) {
                    String sourceHashed = rehashId(sourceRaw, salt);
                    String targetHashed = rehashId(targetRaw, salt);

                    if (nodeIds.add(sourceHashed)) {
                        nodes.add(Map.of("id", sourceHashed, "role", "ASSOCIATE", "entity_type", "ACCOUNT"));
                    }
                    if (nodeIds.add(targetHashed)) {
                        nodes.add(Map.of("id", targetHashed, "role", "ASSOCIATE", "entity_type", "ACCOUNT"));
                    }

                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", sourceHashed);
                    edge.put("target", targetHashed);
                    edge.put("amount", tx.getAmount() != null ? tx.getAmount().doubleValue() : 0.0);
                    edge.put("timestamp", tx.getTimestamp() != null ? tx.getTimestamp().toString() : Instant.now().toString());
                    edges.add(edge);
                }
            }
        }

        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("root_node_id", rootNodeId != null ? rootNodeId : (nodes.isEmpty() ? null : nodes.get(0).get("id")));

        return graph;
    }

    private String rehashId(String rawId, String salt) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(rawId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes((rawId + salt).getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
