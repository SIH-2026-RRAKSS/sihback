package com.sih.dataservice.complaints.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.dto.AssignOfficerRequest;
import com.sih.dataservice.complaints.dto.IncidentDetailDto;
import com.sih.dataservice.complaints.dto.TransitionCaseRequest;
import com.sih.dataservice.complaints.entity.*;
import com.sih.dataservice.complaints.repository.CaseEventRepository;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.notify.entity.Notification;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.Jurisdiction;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseWorkflowServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private CaseEventRepository caseEventRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScopeService scopeService;

    @Mock
    private AuditService auditService;

    private Clock fixedClock;
    private CaseWorkflowService service;

    private User complainant;
    private User policeUser;
    private User cyberOfficerUser;
    private Complaint complaint;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
        service = new CaseWorkflowService(
                complaintRepository,
                caseEventRepository,
                notificationRepository,
                userRepository,
                scopeService,
                auditService,
                fixedClock
        );

        complainant = new User();
        complainant.setId(UUID.randomUUID());
        complainant.setName("Complainant");
        complainant.setRole(UserRole.COMPLAINANT);

        policeUser = new User();
        policeUser.setId(UUID.randomUUID());
        policeUser.setName("Inspector Roy");
        policeUser.setRole(UserRole.POLICE);
        policeUser.setStatus(UserStatus.ACTIVE);

        cyberOfficerUser = new User();
        cyberOfficerUser.setId(UUID.randomUUID());
        cyberOfficerUser.setName("Cyber Officer Sharma");
        cyberOfficerUser.setRole(UserRole.CYBER_OFFICER);
        cyberOfficerUser.setStatus(UserStatus.ACTIVE);

        Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setId(UUID.randomUUID());
        jurisdiction.setName("Cyber Crime PS");
        jurisdiction.setPath("/OD/KHORDHA/");

        complaint = new Complaint();
        complaint.setId(UUID.randomUUID());
        complaint.setHumanReference("CC-2026-000001");
        complaint.setComplainant(complainant);
        complaint.setJurisdiction(jurisdiction);
        complaint.setStatus(ComplaintStatus.FILED);
    }

    @Test
    void assignOfficerTransitionsFromFiledToAssigned() {
        UserPrincipal policePrincipal = UserPrincipal.fromUser(policeUser);
        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));
        when(userRepository.findById(policeUser.getId())).thenReturn(Optional.of(policeUser));
        when(complaintRepository.save(any(Complaint.class))).thenReturn(complaint);

        IncidentDetailDto result = service.assignOfficer(
                complaint.getId(), policeUser.getId(), policePrincipal, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(ComplaintStatus.ASSIGNED);
        verify(caseEventRepository).save(any(CaseEvent.class));
        verify(notificationRepository).save(any(Notification.class));
        verify(auditService).log(eq(policeUser.getId()), eq("POLICE"), eq("TRANSITION_STATUS"),
                eq("COMPLAINT"), eq(complaint.getId().toString()), anyString(), eq("127.0.0.1"));
    }

    @Test
    void allowsValidStatusTransitionFromAssignedToUnderInvestigation() {
        complaint.setStatus(ComplaintStatus.ASSIGNED);
        UserPrincipal policePrincipal = UserPrincipal.fromUser(policeUser);

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));
        when(userRepository.findById(policeUser.getId())).thenReturn(Optional.of(policeUser));
        when(complaintRepository.save(any(Complaint.class))).thenReturn(complaint);

        TransitionCaseRequest request = new TransitionCaseRequest(
                ComplaintStatus.UNDER_INVESTIGATION, "Investigation started", null);

        IncidentDetailDto result = service.transitionCase(
                complaint.getId(), request, policePrincipal, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(ComplaintStatus.UNDER_INVESTIGATION);
        verify(caseEventRepository).save(any(CaseEvent.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void rejectsInvalidStateTransition() {
        // FILED cannot jump directly to UNDER_INVESTIGATION without being ASSIGNED
        complaint.setStatus(ComplaintStatus.FILED);
        UserPrincipal policePrincipal = UserPrincipal.fromUser(policeUser);

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));

        TransitionCaseRequest request = new TransitionCaseRequest(
                ComplaintStatus.UNDER_INVESTIGATION, "Direct jump", null);

        assertThatThrownBy(() -> service.transitionCase(complaint.getId(), request, policePrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void onlyCyberOfficerCanCloseCases() {
        complaint.setStatus(ComplaintStatus.UNDER_INVESTIGATION);
        UserPrincipal policePrincipal = UserPrincipal.fromUser(policeUser);

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));

        TransitionCaseRequest closeRequest = new TransitionCaseRequest(
                ComplaintStatus.CLOSED_FRAUD, "Closing as fraud", CaseLabel.FRAUD);

        // Police attempting to close must be rejected with 403 Forbidden
        assertThatThrownBy(() -> service.transitionCase(complaint.getId(), closeRequest, policePrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only CYBER_OFFICER can close cases");
    }

    @Test
    void cyberOfficerCanCloseCaseAndApplyLabel() {
        complaint.setStatus(ComplaintStatus.UNDER_INVESTIGATION);
        UserPrincipal cyberPrincipal = UserPrincipal.fromUser(cyberOfficerUser);

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));
        when(userRepository.findById(cyberOfficerUser.getId())).thenReturn(Optional.of(cyberOfficerUser));
        when(complaintRepository.save(any(Complaint.class))).thenReturn(complaint);

        TransitionCaseRequest closeRequest = new TransitionCaseRequest(
                ComplaintStatus.CLOSED_FRAUD, "Verified fraud", CaseLabel.FRAUD);

        IncidentDetailDto result = service.transitionCase(
                complaint.getId(), closeRequest, cyberPrincipal, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(ComplaintStatus.CLOSED_FRAUD);
        assertThat(result.getLabel()).isEqualTo(CaseLabel.FRAUD);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void cyberOfficerCanRelabelCasePostClosure() {
        complaint.setStatus(ComplaintStatus.CLOSED_FRAUD);
        complaint.setLabel(CaseLabel.FRAUD);
        UserPrincipal cyberPrincipal = UserPrincipal.fromUser(cyberOfficerUser);

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));
        when(userRepository.findById(cyberOfficerUser.getId())).thenReturn(Optional.of(cyberOfficerUser));
        when(complaintRepository.save(any(Complaint.class))).thenReturn(complaint);

        IncidentDetailDto result = service.updateCaseLabel(
                complaint.getId(), CaseLabel.NOT_FRAUD, "Evidence disproved fraud allegation", cyberPrincipal, "127.0.0.1");

        assertThat(result.getLabel()).isEqualTo(CaseLabel.NOT_FRAUD);
        assertThat(result.getStatus()).isEqualTo(ComplaintStatus.CLOSED_NOT_FRAUD);
        verify(caseEventRepository).save(any(CaseEvent.class));
        verify(auditService).log(eq(cyberOfficerUser.getId()), eq("CYBER_OFFICER"), eq("RELABEL_CASE"),
                eq("COMPLAINT"), eq(complaint.getId().toString()), anyString(), eq("127.0.0.1"));
    }

    @Test
    void nonCyberOfficerCannotRelabelCase() {
        complaint.setStatus(ComplaintStatus.CLOSED_FRAUD);
        complaint.setLabel(CaseLabel.FRAUD);
        UserPrincipal policePrincipal = UserPrincipal.fromUser(policeUser);

        assertThatThrownBy(() -> service.updateCaseLabel(
                complaint.getId(), CaseLabel.NOT_FRAUD, "Attempted relabel", policePrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only CYBER_OFFICER can change case labels");
    }
}
