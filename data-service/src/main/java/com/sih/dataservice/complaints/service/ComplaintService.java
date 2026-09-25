package com.sih.dataservice.complaints.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.dto.*;
import com.sih.dataservice.complaints.entity.*;
import com.sih.dataservice.complaints.repository.*;
import com.sih.dataservice.notify.entity.Notification;
import com.sih.dataservice.notify.entity.NotificationChannel;
import com.sih.dataservice.notify.entity.NotificationStatus;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.Jurisdiction;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.sih.dataservice.ml.repository.PredictionRepository;
import com.sih.dataservice.ml.entity.Prediction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintService.class);

    private final ComplaintRepository complaintRepository;
    private final ComplaintAccountRepository complaintAccountRepository;
    private final FinancialEntityRepository financialEntityRepository;
    private final CaseEventRepository caseEventRepository;
    private final EvidenceRepository evidenceRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BankRepository bankRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final ReferenceNumberService referenceNumberService;
    private final EvidenceStorageService evidenceStorageService;
    private final CryptoService cryptoService;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final Clock clock;
    private final PredictionRepository predictionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ComplaintService(
            ComplaintRepository complaintRepository,
            ComplaintAccountRepository complaintAccountRepository,
            FinancialEntityRepository financialEntityRepository,
            CaseEventRepository caseEventRepository,
            EvidenceRepository evidenceRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            BankRepository bankRepository,
            JurisdictionRepository jurisdictionRepository,
            ReferenceNumberService referenceNumberService,
            EvidenceStorageService evidenceStorageService,
            CryptoService cryptoService,
            ScopeService scopeService,
            AuditService auditService,
            Clock clock, PredictionRepository predictionRepository) {
        this.complaintRepository = complaintRepository;
        this.complaintAccountRepository = complaintAccountRepository;
        this.financialEntityRepository = financialEntityRepository;
        this.caseEventRepository = caseEventRepository;
        this.evidenceRepository = evidenceRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.bankRepository = bankRepository;
        this.jurisdictionRepository = jurisdictionRepository;
        this.referenceNumberService = referenceNumberService;
        this.evidenceStorageService = evidenceStorageService;
        this.cryptoService = cryptoService;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.clock = clock;
        this.predictionRepository = predictionRepository;
    }

    /**
     * Files a new cybercrime complaint (FR-CMP-1).
     */
    @Transactional
    public ComplaintResponseDto createComplaint(CreateComplaintRequest request, UserPrincipal principal, String clientIp) {
        User complainant = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("Complainant user record not found"));

        // Determine jurisdiction
        Jurisdiction jurisdiction = null;
        if (request.getJurisdictionId() != null) {
            jurisdiction = jurisdictionRepository.findById(request.getJurisdictionId()).orElse(null);
        } else if (request.getDistrict() != null && !request.getDistrict().isBlank()) {
            jurisdiction = jurisdictionRepository.findByNameIgnoreCase(request.getDistrict().trim()).orElse(null);
        }
        if (jurisdiction == null) {
            jurisdiction = complainant.getJurisdiction();
        }

        String reference = referenceNumberService.generateReference();
        Instant now = Instant.now(clock);

        Complaint complaint = new Complaint();
        complaint.setHumanReference(reference);
        complaint.setComplainant(complainant);
        complaint.setJurisdiction(jurisdiction);
        complaint.setFraudType(request.getFraudType());
        complaint.setAmount(request.getAmount());
        complaint.setIncidentTime(request.getIncidentTime());
        complaint.setDescriptionOriginal(request.getDescriptionOriginal());
        complaint.setDescriptionLanguage(request.getDescriptionLanguage() != null ? request.getDescriptionLanguage() : "en");
        complaint.setChannel(request.getChannel() != null ? request.getChannel() : ComplaintChannel.WEB);
        complaint.setStatus(ComplaintStatus.FILED);
        complaint.setCreatedAt(now);
        complaint.setUpdatedAt(now);

        Complaint savedComplaint = complaintRepository.save(complaint);

        // Process suspect / victim accounts with HMAC hashing & AES encryption (FR-CMP-3, NFR-SEC-2)
        if (request.getAccounts() != null) {
            for (AccountRequestDto accDto : request.getAccounts()) {
                if (accDto.getAccountNumber() == null || accDto.getAccountNumber().isBlank()) {
                    continue;
                }
                String accountHash = cryptoService.computeHmac(accDto.getAccountNumber());
                String accountEncrypted = cryptoService.encrypt(accDto.getAccountNumber());

                final Bank bank = (accDto.getBankId() != null)
                        ? bankRepository.findById(accDto.getBankId()).orElse(null)
                        : null;

                FinancialEntity entity = financialEntityRepository.findByAccountHash(accountHash)
                        .orElseGet(() -> financialEntityRepository.save(new FinancialEntity(
                                accountHash,
                                accountEncrypted,
                                bank,
                                accDto.getEntityType()
                        )));

                ComplaintAccount complaintAccount = new ComplaintAccount(savedComplaint, entity, accDto.getRole());
                complaintAccountRepository.save(complaintAccount);
            }
        }

        // Record initial case event
        CaseEvent initialEvent = new CaseEvent(
                savedComplaint,
                complainant,
                null,
                ComplaintStatus.FILED,
                "Complaint filed via " + savedComplaint.getChannel()
        );
        caseEventRepository.save(initialEvent);

        // Audit log
        auditService.log(complainant.getId(), principal.getRole().name(), "FILE_COMPLAINT", "COMPLAINT",
                savedComplaint.getId().toString(), "{\"reference\":\"" + reference + "\"}", clientIp);

        // Queue SMS / Notification (FR-NOT-1)
        Notification notification = new Notification(
                complainant,
                NotificationChannel.SMS,
                "COMPLAINT_FILED",
                String.format("{\"reference\":\"%s\",\"status\":\"RECEIVED\"}", reference)
        );
        notificationRepository.save(notification);

        log.info("Registered complaint reference={} id={} for user={}", reference, savedComplaint.getId(), complainant.getId());
        return ComplaintResponseDto.fromEntity(savedComplaint);
    }

    /**
     * Lists complaints filed by the complainant (FR-CMP-2).
     */
    @Transactional(readOnly = true)
    public Page<ComplaintResponseDto> getComplainantComplaints(UserPrincipal principal, Pageable pageable) {
        return complaintRepository.findByComplainantId(principal.getId(), pageable)
                .map(ComplaintResponseDto::fromEntity);
    }

    /**
     * Gets single complaint for complainant. Returns 404 if not found or belongs to another user (NFR-SEC-1).
     */
    @Transactional(readOnly = true)
    public ComplaintResponseDto getComplainantComplaintById(UUID complaintId, UserPrincipal principal) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("Complaint not found"));

        if (!complaint.getComplainant().getId().equals(principal.getId())) {
            throw ApiException.notFound("Complaint not found");
        }

        return ComplaintResponseDto.fromEntity(complaint);
    }

    /**
     * Uploads evidence for a complaint (FR-CMP-4).
     */
    @Transactional
    public EvidenceDto uploadEvidence(UUID complaintId, MultipartFile file, UserPrincipal principal) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("Complaint not found"));

        // Scope verification
        if (principal.getRole() == UserRole.COMPLAINANT) {
            if (!complaint.getComplainant().getId().equals(principal.getId())) {
                throw ApiException.notFound("Complaint not found");
            }
        } else {
            String path = complaint.getJurisdiction() != null ? complaint.getJurisdiction().getPath() : null;
            scopeService.enforceCaseAccess(principal, complaint.getComplainant().getId(), null, path);
        }

        User uploader = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("Uploader not found"));

        return evidenceStorageService.storeEvidence(complaint, uploader, file);
    }

    /**
     * Staff view of full incident detail (FR-TRI-2, FR-WKF-1).
     */
    @Transactional(readOnly = true)
    public IncidentDetailDto getIncidentDetail(UUID complaintId, UserPrincipal principal) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        String path = complaint.getJurisdiction() != null ? complaint.getJurisdiction().getPath() : null;
        scopeService.enforceCaseAccess(principal, complaint.getComplainant().getId(), null, path);

        IncidentDetailDto dto = IncidentDetailDto.fromEntity(complaint);
        enrichWithPrediction(dto);

        // Load associated accounts
        List<ComplaintAccountDto> accounts = complaintAccountRepository.findByComplaintId(complaintId)
                .stream()
                .map(ComplaintAccountDto::fromEntity)
                .toList();
        dto.setAccounts(accounts);

        // Load events
        List<CaseEventDto> events = caseEventRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId)
                .stream()
                .map(CaseEventDto::fromEntity)
                .toList();
        dto.setEvents(events);

        // Load evidence
        List<EvidenceDto> evidence = evidenceRepository.findByComplaintId(complaintId)
                .stream()
                .map(EvidenceDto::fromEntity)
                .toList();
        dto.setEvidence(evidence);

        return dto;
    }

    /**
     * Lists incidents for staff according to role-based scope isolation.
     */
    @Transactional(readOnly = true)
    public Page<IncidentDetailDto> listIncidents(UserPrincipal principal, ComplaintStatus status, Pageable pageable) {
        UserRole role = principal.getRole();

        Page<Complaint> page;
        switch (role) {
            case ADMIN:
            case CYBER_OFFICER:
                if (status != null) {
                    page = complaintRepository.findByStatus(status, pageable);
                } else {
                    page = complaintRepository.findAll(pageable);
                }
                break;

            case POLICE:
                if (principal.getJurisdictionPath() == null) {
                    page = Page.empty(pageable);
                } else {
                    page = complaintRepository.findByJurisdictionPathPrefix(principal.getJurisdictionPath(), pageable);
                }
                break;

            case BANK_EMPLOYEE:
            case BANK_MANAGER:
                if (principal.getBankId() == null) {
                    page = Page.empty(pageable);
                } else {
                    page = complaintRepository.findByInvolvedBankId(principal.getBankId(), pageable);
                }
                break;

            default:
                throw ApiException.forbidden("Access denied to incident list");
        }

        return page.map(IncidentDetailDto::fromEntity).map(this::enrichWithPrediction);
    }

    private IncidentDetailDto enrichWithPrediction(IncidentDetailDto dto) {
        predictionRepository.findTopByComplaintIdOrderByCreatedAtDesc(dto.getId()).ifPresent(pred -> {
            dto.setRiskScore(pred.getRisk() != null ? pred.getRisk().doubleValue() : null);
            dto.setConfidenceTier(pred.getConfidence() != null ? String.valueOf(pred.getConfidence().doubleValue()) : null);
            if (pred.getModelVersion() != null) dto.setModelVersion(pred.getModelVersion().getName());
            
            try {
                if (pred.getExplanation() != null && !pred.getExplanation().equals("{}")) {
                    JsonNode expl = objectMapper.readTree(pred.getExplanation());
                    JsonNode topNodes = expl.path("topNodes");
                    if (topNodes.isArray() && topNodes.size() > 0) {
                        java.util.List<String> nodes = new java.util.ArrayList<>();
                        for (JsonNode n : topNodes) {
                            if (n.has("id")) nodes.add(n.get("id").asText());
                        }
                        dto.setTopNodes(nodes);
                        if (!nodes.isEmpty()) dto.setTopTerminalId(nodes.get(0));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse prediction explanation json for complaint {}", dto.getId());
            }
            
            // Map numeric confidence back to string tier for frontend
            if (dto.getConfidenceTier() != null) {
                double conf = Double.parseDouble(dto.getConfidenceTier());
                if (conf >= 0.90) dto.setConfidenceTier("HIGH_CONFIDENCE");
                else if (conf >= 0.75) dto.setConfidenceTier("FIRST_TIME_RING_CANDIDATE");
                else if (conf >= 0.60) dto.setConfidenceTier("MEDIUM_CONFIDENCE");
                else if (conf >= 0.50) dto.setConfidenceTier("UNCLASSIFIED");
                else dto.setConfidenceTier("NORMAL");
            } else {
                dto.setConfidenceTier("UNCLASSIFIED");
            }
        });
        return dto;
    }
}

