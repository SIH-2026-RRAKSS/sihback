package com.sih.dataservice.freeze.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.entity.*;
import com.sih.dataservice.complaints.repository.CaseEventRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.complaints.repository.FinancialEntityRepository;
import com.sih.dataservice.freeze.dto.CreateFreezeRequestDto;
import com.sih.dataservice.freeze.dto.FreezeRequestResponseDto;
import com.sih.dataservice.freeze.dto.RespondFreezeRequestDto;
import com.sih.dataservice.freeze.entity.FreezeRequest;
import com.sih.dataservice.freeze.entity.FreezeRequestStatus;
import com.sih.dataservice.freeze.repository.FreezeRequestRepository;
import com.sih.dataservice.notify.entity.Notification;
import com.sih.dataservice.notify.entity.NotificationChannel;
import com.sih.dataservice.notify.entity.NotificationStatus;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.Bank;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class FreezeRequestService {

    private static final Logger log = LoggerFactory.getLogger(FreezeRequestService.class);

    private final FreezeRequestRepository freezeRequestRepository;
    private final ComplaintRepository complaintRepository;
    private final FinancialEntityRepository financialEntityRepository;
    private final UserRepository userRepository;
    private final CaseEventRepository caseEventRepository;
    private final NotificationRepository notificationRepository;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FreezeRequestService(
            FreezeRequestRepository freezeRequestRepository,
            ComplaintRepository complaintRepository,
            FinancialEntityRepository financialEntityRepository,
            UserRepository userRepository,
            CaseEventRepository caseEventRepository,
            NotificationRepository notificationRepository,
            ScopeService scopeService,
            AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.freezeRequestRepository = freezeRequestRepository;
        this.complaintRepository = complaintRepository;
        this.financialEntityRepository = financialEntityRepository;
        this.userRepository = userRepository;
        this.caseEventRepository = caseEventRepository;
        this.notificationRepository = notificationRepository;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Police or Cyber Officer raises a freeze/info request against an account/entity (FR-FRZ-1).
     * Resolution deadline is fixed at 7 days from creation (FR-FRZ-3).
     */
    @Transactional
    public FreezeRequestResponseDto createFreezeRequest(CreateFreezeRequestDto dto, UserPrincipal principal, String clientIp) {
        if (principal.getRole() != UserRole.POLICE && principal.getRole() != UserRole.CYBER_OFFICER) {
            throw ApiException.forbidden("Only police officers and cyber officers can raise freeze requests");
        }

        Complaint complaint = complaintRepository.findById(dto.getComplaintId())
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        if (!isComplaintInScope(principal, complaint)) {
            throw ApiException.notFound("Incident not found");
        }

        FinancialEntity entity = financialEntityRepository.findById(dto.getEntityId())
                .orElseThrow(() -> ApiException.notFound("Target entity not found"));

        Bank bank = entity.getBank();
        if (bank == null) {
            throw ApiException.badRequest("Target entity has no associated bank for freeze request");
        }

        User actor = userRepository.findById(principal.getId()).orElse(null);
        Instant now = Instant.now(clock);
        Instant dueAt = now.plus(Duration.ofDays(7));

        FreezeRequest freezeRequest = new FreezeRequest(complaint, entity, bank, actor, now, dueAt);
        freezeRequest = freezeRequestRepository.save(freezeRequest);

        // Transition case status to FREEZE_REQUESTED if in UNDER_INVESTIGATION
        if (complaint.getStatus() == ComplaintStatus.UNDER_INVESTIGATION) {
            complaint.setStatus(ComplaintStatus.FREEZE_REQUESTED);
            complaint.setUpdatedAt(now);
            complaintRepository.save(complaint);

            String eventNote = "Freeze request raised against entity " + entity.getAccountHash() +
                    " at " + bank.getName() + (dto.getNote() != null ? ": " + dto.getNote() : "");
            caseEventRepository.save(new CaseEvent(
                    complaint, actor, ComplaintStatus.UNDER_INVESTIGATION, ComplaintStatus.FREEZE_REQUESTED, eventNote
            ));

            queueComplainantNotification(complaint, ComplaintStatus.FREEZE_REQUESTED);
        }

        auditService.log(
                principal.getId(),
                principal.getRole().name(),
                "FREEZE_REQUESTED",
                "FREEZE_REQUEST",
                freezeRequest.getId().toString(),
                toJson(Map.of(
                        "complaintId", complaint.getId().toString(),
                        "entityId", entity.getId().toString(),
                        "bankId", bank.getId().toString(),
                        "dueAt", dueAt.toString()
                )),
                clientIp
        );

        log.info("Freeze request id={} raised for complaint={} entity={} bank={} dueAt={}",
                freezeRequest.getId(), complaint.getHumanReference(), entity.getAccountHash(), bank.getName(), dueAt);

        return FreezeRequestResponseDto.fromEntity(freezeRequest, now);
    }

    /**
     * Bank responds to freeze/info request (FR-FRZ-2).
     * Transitions case status to BANK_RESPONDED.
     */
    @Transactional
    public FreezeRequestResponseDto respondFreezeRequest(UUID freezeRequestId, RespondFreezeRequestDto dto, UserPrincipal principal, String clientIp) {
        if (principal.getRole() != UserRole.BANK_EMPLOYEE && principal.getRole() != UserRole.BANK_MANAGER) {
            throw ApiException.forbidden("Only bank staff can respond to freeze requests");
        }

        FreezeRequest request = freezeRequestRepository.findById(freezeRequestId)
                .orElseThrow(() -> ApiException.notFound("Freeze request not found"));

        // Scope check: must belong to caller's bank
        if (principal.getBankId() == null || !principal.getBankId().equals(request.getBank().getId())) {
            throw ApiException.notFound("Freeze request not found");
        }

        if (request.getStatus() != FreezeRequestStatus.OPEN) {
            throw ApiException.badRequest("Freeze request has already been processed with status " + request.getStatus());
        }

        if (dto.getStatus() == FreezeRequestStatus.OPEN) {
            throw ApiException.badRequest("Response status cannot be OPEN");
        }

        User actor = userRepository.findById(principal.getId()).orElse(null);
        Instant now = Instant.now(clock);

        request.setStatus(dto.getStatus());
        request.setResponseNote(dto.getResponseNote());
        request.setRespondedBy(actor);
        request.setRespondedAt(now);
        freezeRequestRepository.save(request);

        // Transition case status to BANK_RESPONDED if in FREEZE_REQUESTED
        Complaint complaint = request.getComplaint();
        if (complaint.getStatus() == ComplaintStatus.FREEZE_REQUESTED) {
            complaint.setStatus(ComplaintStatus.BANK_RESPONDED);
            complaint.setUpdatedAt(now);
            complaintRepository.save(complaint);

            String eventNote = "Bank " + request.getBank().getName() + " responded with " +
                    dto.getStatus() + ": " + dto.getResponseNote();
            caseEventRepository.save(new CaseEvent(
                    complaint, actor, ComplaintStatus.FREEZE_REQUESTED, ComplaintStatus.BANK_RESPONDED, eventNote
            ));

            queueComplainantNotification(complaint, ComplaintStatus.BANK_RESPONDED);
        }

        auditService.log(
                principal.getId(),
                principal.getRole().name(),
                "FREEZE_RESPONDED",
                "FREEZE_REQUEST",
                request.getId().toString(),
                toJson(Map.of(
                        "complaintId", complaint.getId().toString(),
                        "bankId", request.getBank().getId().toString(),
                        "responseStatus", dto.getStatus().name(),
                        "responseNote", dto.getResponseNote()
                )),
                clientIp
        );

        log.info("Bank {} responded to freeze request id={} with result={}",
                request.getBank().getName(), request.getId(), dto.getStatus());

        return FreezeRequestResponseDto.fromEntity(request, now);
    }

    /**
     * Retrieves a single freeze request by id with strict scope verification.
     */
    @Transactional(readOnly = true)
    public FreezeRequestResponseDto getFreezeRequest(UUID id, UserPrincipal principal) {
        FreezeRequest request = freezeRequestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Freeze request not found"));

        assertFreezeRequestInScope(principal, request);
        return FreezeRequestResponseDto.fromEntity(request, Instant.now(clock));
    }

    /**
     * Retrieves paged freeze requests scoped to the caller's role.
     */
    @Transactional(readOnly = true)
    public Page<FreezeRequestResponseDto> listFreezeRequests(UserPrincipal principal, Pageable pageable) {
        Instant now = Instant.now(clock);

        if (principal.getRole() == UserRole.CYBER_OFFICER) {
            return freezeRequestRepository.findAll(pageable)
                    .map(f -> FreezeRequestResponseDto.fromEntity(f, now));
        }

        if (principal.getRole() == UserRole.BANK_EMPLOYEE || principal.getRole() == UserRole.BANK_MANAGER) {
            if (principal.getBankId() == null) {
                return Page.empty(pageable);
            }
            return freezeRequestRepository.findByBankId(principal.getBankId(), pageable)
                    .map(f -> FreezeRequestResponseDto.fromEntity(f, now));
        }

        if (principal.getRole() == UserRole.POLICE) {
            // Find complaints in police officer's jurisdiction
            List<UUID> complaintIds = complaintRepository.findAll().stream()
                    .filter(c -> isComplaintInScope(principal, c))
                    .map(Complaint::getId)
                    .toList();

            if (complaintIds.isEmpty()) {
                return Page.empty(pageable);
            }

            return freezeRequestRepository.findByComplaintIdIn(complaintIds, pageable)
                    .map(f -> FreezeRequestResponseDto.fromEntity(f, now));
        }

        return Page.empty(pageable);
    }

    /**
     * Retrieves all freeze requests for a given incident/complaint.
     */
    @Transactional(readOnly = true)
    public List<FreezeRequestResponseDto> getFreezeRequestsForComplaint(UUID complaintId, UserPrincipal principal) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("Incident not found"));

        if (!isComplaintInScope(principal, complaint)) {
            throw ApiException.notFound("Incident not found");
        }

        Instant now = Instant.now(clock);
        List<FreezeRequest> requests = freezeRequestRepository.findByComplaintId(complaintId);

        // If bank role, filter to only requests for own bank
        if (principal.getRole() == UserRole.BANK_EMPLOYEE || principal.getRole() == UserRole.BANK_MANAGER) {
            requests = requests.stream()
                    .filter(r -> principal.getBankId() != null && principal.getBankId().equals(r.getBank().getId()))
                    .toList();
        }

        return requests.stream()
                .map(r -> FreezeRequestResponseDto.fromEntity(r, now))
                .toList();
    }

    /**
     * Checks and executes 7-day SLA triggers:
     * - Day 5 Reminder: if OPEN and currentTime >= raisedAt + 5 days, sends reminder once.
     * - Day 7 Escalation: if OPEN and currentTime >= dueAt (raisedAt + 7 days), flags escalation once.
     */
    @Transactional
    public Map<String, Integer> checkSlaBreachesAndReminders(Instant currentTime) {
        // 1. Day 5 Reminders (raisedAt <= currentTime - 5 days)
        Instant day5Cutoff = currentTime.minus(Duration.ofDays(5));
        List<FreezeRequest> pendingReminders = freezeRequestRepository.findPendingReminders(day5Cutoff);
        int reminderCount = 0;

        for (FreezeRequest req : pendingReminders) {
            req.setReminderSentAt(currentTime);
            freezeRequestRepository.save(req);
            reminderCount++;

            auditService.log(
                    null,
                    "SYSTEM",
                    "FREEZE_REMINDER_SENT",
                    "FREEZE_REQUEST",
                    req.getId().toString(),
                    toJson(Map.of(
                            "complaintId", req.getComplaint().getId().toString(),
                            "bankId", req.getBank().getId().toString(),
                            "dueAt", req.getDueAt().toString()
                    )),
                    "127.0.0.1"
            );

            log.warn("SLA Reminder (Day 5) triggered for freeze request id={} bank={}", req.getId(), req.getBank().getName());
        }

        // 2. Overdue Escalations (dueAt <= currentTime)
        List<FreezeRequest> overdueRequests = freezeRequestRepository.findOverdueEscalations(currentTime);
        int escalationCount = 0;

        for (FreezeRequest req : overdueRequests) {
            req.setEscalatedAt(currentTime);
            freezeRequestRepository.save(req);
            escalationCount++;

            auditService.log(
                    null,
                    "SYSTEM",
                    "FREEZE_ESCALATED",
                    "FREEZE_REQUEST",
                    req.getId().toString(),
                    toJson(Map.of(
                            "complaintId", req.getComplaint().getId().toString(),
                            "bankId", req.getBank().getId().toString(),
                            "dueAt", req.getDueAt().toString(),
                            "escalatedTo", "BANK_MANAGER,CYBER_DEPARTMENT"
                    )),
                    "127.0.0.1"
            );

            log.error("SLA Breach / Escalation triggered for freeze request id={} bank={} dueAt={}",
                    req.getId(), req.getBank().getName(), req.getDueAt());
        }

        return Map.of("remindersSent", reminderCount, "escalationsTriggered", escalationCount);
    }

    private boolean isComplaintInScope(UserPrincipal principal, Complaint complaint) {
        UUID complainantId = complaint.getComplainant() != null ? complaint.getComplainant().getId() : null;
        String path = complaint.getJurisdiction() != null ? complaint.getJurisdiction().getPath() : null;
        return scopeService.isCaseInScope(principal, complainantId, null, path);
    }

    private void assertFreezeRequestInScope(UserPrincipal principal, FreezeRequest request) {
        if (principal.getRole() == UserRole.CYBER_OFFICER) {
            return;
        }

        if (principal.getRole() == UserRole.BANK_EMPLOYEE || principal.getRole() == UserRole.BANK_MANAGER) {
            if (principal.getBankId() == null || !principal.getBankId().equals(request.getBank().getId())) {
                throw ApiException.notFound("Freeze request not found");
            }
            return;
        }

        if (principal.getRole() == UserRole.POLICE) {
            if (!isComplaintInScope(principal, request.getComplaint())) {
                throw ApiException.notFound("Freeze request not found");
            }
            return;
        }

        throw ApiException.notFound("Freeze request not found");
    }

    private void queueComplainantNotification(Complaint complaint, ComplaintStatus newStatus) {
        PublicStatus publicStatus = PublicStatus.fromInternalStatus(newStatus);
        Notification notification = new Notification(
                complaint.getComplainant(),
                NotificationChannel.WHATSAPP,
                "STATUS_UPDATE",
                toJson(Map.of(
                        "reference", complaint.getHumanReference(),
                        "internalStatus", newStatus.name(),
                        "publicStatus", publicStatus.getDisplayName()
                ))
        );
        notification.setStatus(NotificationStatus.QUEUED);
        notificationRepository.save(notification);
    }

    private String toJson(Map<String, ?> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
