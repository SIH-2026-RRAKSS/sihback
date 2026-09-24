package com.sih.dataservice.complaints.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.dto.CaseEventDto;
import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.complaints.dto.TransitionCaseRequest;
import com.sih.dataservice.complaints.entity.CaseEvent;
import com.sih.dataservice.complaints.entity.CaseLabel;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintChannel;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.entity.PublicStatus;
import com.sih.dataservice.complaints.repository.CaseEventRepository;
import com.sih.dataservice.complaints.repository.ComplaintAccountRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.complaints.repository.EvidenceRepository;
import com.sih.dataservice.notify.entity.Notification;
import com.sih.dataservice.notify.entity.NotificationChannel;
import com.sih.dataservice.notify.entity.NotificationStatus;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CaseWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(CaseWorkflowService.class);

    // State transition rules per design.md Section 6
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = Map.of(
            ComplaintStatus.FILED, Set.of(ComplaintStatus.TRIAGED, ComplaintStatus.ASSIGNED, ComplaintStatus.CLOSED_NOT_FRAUD),
            ComplaintStatus.TRIAGED, Set.of(ComplaintStatus.ASSIGNED, ComplaintStatus.CLOSED_NOT_FRAUD),
            ComplaintStatus.ASSIGNED, Set.of(ComplaintStatus.UNDER_INVESTIGATION, ComplaintStatus.CLOSED_NOT_FRAUD),
            ComplaintStatus.UNDER_INVESTIGATION, Set.of(ComplaintStatus.FREEZE_REQUESTED, ComplaintStatus.BANK_RESPONDED, ComplaintStatus.CLOSED_FRAUD, ComplaintStatus.CLOSED_NOT_FRAUD),
            ComplaintStatus.FREEZE_REQUESTED, Set.of(ComplaintStatus.BANK_RESPONDED, ComplaintStatus.UNDER_INVESTIGATION, ComplaintStatus.CLOSED_FRAUD, ComplaintStatus.CLOSED_NOT_FRAUD),
            ComplaintStatus.BANK_RESPONDED, Set.of(ComplaintStatus.UNDER_INVESTIGATION, ComplaintStatus.FREEZE_REQUESTED, ComplaintStatus.CLOSED_FRAUD, ComplaintStatus.CLOSED_NOT_FRAUD),
            ComplaintStatus.CLOSED_FRAUD, Set.of(),
            ComplaintStatus.CLOSED_NOT_FRAUD, Set.of()
    );

    private final ComplaintRepository complaintRepository;
    private final CaseEventRepository caseEventRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final Clock clock;

    public CaseWorkflowService(
            ComplaintRepository complaintRepository,
            CaseEventRepository caseEventRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ScopeService scopeService,
            AuditService auditService,
            Clock clock) {
        this.complaintRepository = complaintRepository;
        this.caseEventRepository = caseEventRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Assigns or reassigns an officer to a complaint.
     */
    @Transactional
    public IncidentDetailDto assignOfficer(UUID complaintId, UUID officerId, UserPrincipal principal, String clientIp) {
        Complaint complaint = getComplaintAndCheckScope(complaintId, principal);

        if (principal.getRole() != UserRole.POLICE && principal.getRole() != UserRole.CYBER_OFFICER) {
            throw ApiException.forbidden("Only police officers and cyber officers can assign cases");
        }

        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> ApiException.badRequest("Target officer not found"));

        if (officer.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.badRequest("Cannot assign an inactive officer");
        }

        if (officer.getRole() != UserRole.POLICE && officer.getRole() != UserRole.CYBER_OFFICER) {
            throw ApiException.badRequest("Officer must be POLICE or CYBER_OFFICER");
        }

        User actor = userRepository.findById(principal.getId()).orElse(null);
        ComplaintStatus fromStatus = complaint.getStatus();
        complaint.setAssignedOfficer(officer);
        complaint.setUpdatedAt(Instant.now(clock));

        // If newly filed, transition to ASSIGNED
        if (fromStatus == ComplaintStatus.FILED) {
            complaint.setStatus(ComplaintStatus.ASSIGNED);
            recordCaseEventAndAudit(complaint, actor, fromStatus, ComplaintStatus.ASSIGNED, 
                    "Assigned to officer: " + officer.getName(), clientIp);
            queueComplainantNotification(complaint, ComplaintStatus.ASSIGNED);
        } else {
            recordCaseEventAndAudit(complaint, actor, fromStatus, fromStatus,
                    "Reassigned to officer: " + officer.getName(), clientIp);
        }

        complaintRepository.save(complaint);
        return IncidentDetailDto.fromEntity(complaint);
    }

    /**
     * Executes an atomic status transition with validation, audit logging, and queued notification.
     */
    @Transactional
    public IncidentDetailDto transitionCase(UUID complaintId, TransitionCaseRequest request, UserPrincipal principal, String clientIp) {
        Complaint complaint = getComplaintAndCheckScope(complaintId, principal);

        ComplaintStatus fromStatus = complaint.getStatus();
        ComplaintStatus toStatus = request.getToStatus();

        if (fromStatus == toStatus) {
            throw ApiException.badRequest("Case is already in status " + toStatus);
        }

        Set<ComplaintStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of());
        if (!allowedTargets.contains(toStatus)) {
            throw ApiException.badRequest(String.format("Invalid status transition from %s to %s", fromStatus, toStatus));
        }

        // Operational transition role validation
        if (principal.getRole() != UserRole.POLICE && principal.getRole() != UserRole.CYBER_OFFICER) {
            throw ApiException.forbidden("Only police officers and cyber officers can transition case status");
        }

        User actor = userRepository.findById(principal.getId()).orElse(null);

        // Closure rules: ONLY CYBER_OFFICER can close cases (FR-WKF-3, FR-LBL-1)
        if (toStatus == ComplaintStatus.CLOSED_FRAUD || toStatus == ComplaintStatus.CLOSED_NOT_FRAUD) {
            if (principal.getRole() != UserRole.CYBER_OFFICER) {
                throw ApiException.forbidden("Only CYBER_OFFICER can close cases");
            }

            CaseLabel label = toStatus == ComplaintStatus.CLOSED_FRAUD ? CaseLabel.FRAUD : CaseLabel.NOT_FRAUD;
            if (request.getLabel() != null && request.getLabel() != label) {
                throw ApiException.badRequest("Label " + request.getLabel() + " is incompatible with closure status " + toStatus);
            }
            complaint.setLabel(label);
            complaint.setLabeledBy(actor);
            complaint.setLabeledAt(Instant.now(clock));
        } else if (request.getLabel() != null) {
            // Labeling without closing also requires CYBER_OFFICER
            if (principal.getRole() != UserRole.CYBER_OFFICER) {
                throw ApiException.forbidden("Only CYBER_OFFICER can set case fraud labels");
            }
            complaint.setLabel(request.getLabel());
            complaint.setLabeledBy(actor);
            complaint.setLabeledAt(Instant.now(clock));
        }

        complaint.setStatus(toStatus);
        complaint.setUpdatedAt(Instant.now(clock));
        complaintRepository.save(complaint);

        // Record Case Event and Audit Log
        recordCaseEventAndAudit(complaint, actor, fromStatus, toStatus, request.getNote(), clientIp);

        // Queue complainant notification
        queueComplainantNotification(complaint, toStatus);

        return IncidentDetailDto.fromEntity(complaint);
    }

    private Complaint getComplaintAndCheckScope(UUID complaintId, UserPrincipal principal) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> ApiException.notFound("Case not found"));

        UUID complainantId = complaint.getComplainant() != null ? complaint.getComplainant().getId() : null;
        String jurisdictionPath = complaint.getJurisdiction() != null ? complaint.getJurisdiction().getPath() : null;

        // Enforce scope: throws 404 NOT FOUND if out-of-scope (NFR-SEC-1)
        scopeService.enforceCaseAccess(principal, complainantId, null, jurisdictionPath);
        return complaint;
    }

    private void recordCaseEventAndAudit(Complaint complaint, User actor, ComplaintStatus fromStatus, ComplaintStatus toStatus, String note, String clientIp) {
        CaseEvent event = new CaseEvent(complaint, actor, fromStatus, toStatus, note);
        caseEventRepository.save(event);

        String actorRole = actor != null ? actor.getRole().name() : "SYSTEM";
        UUID actorId = actor != null ? actor.getId() : null;
        String detailJson = String.format("{\"fromStatus\":\"%s\",\"toStatus\":\"%s\",\"note\":\"%s\"}",
                fromStatus, toStatus, note != null ? note.replace("\"", "\\\"") : "");

        auditService.log(actorId, actorRole, "TRANSITION_STATUS", "COMPLAINT", complaint.getId().toString(), detailJson, clientIp);
    }

    private void queueComplainantNotification(Complaint complaint, ComplaintStatus toStatus) {
        if (complaint.getComplainant() == null) {
            return;
        }

        PublicStatus publicStatus = PublicStatus.fromInternalStatus(toStatus);
        String payload = String.format("{\"reference\":\"%s\",\"publicStatus\":\"%s\",\"internalStatus\":\"%s\"}",
                complaint.getHumanReference(), publicStatus.name(), toStatus.name());

        NotificationChannel channel = complaint.getChannel() == ComplaintChannel.WHATSAPP
                ? NotificationChannel.WHATSAPP
                : NotificationChannel.SMS;

        Notification notification = new Notification(
                complaint.getComplainant(),
                channel,
                "CASE_STATUS_UPDATE",
                payload
        );
        notification.setStatus(NotificationStatus.QUEUED);
        notificationRepository.save(notification);
    }
}
