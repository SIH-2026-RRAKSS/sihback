package com.sih.dataservice.freeze.service;

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
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.Bank;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreezeRequestServiceTest {

    @Mock
    private FreezeRequestRepository freezeRequestRepository;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private FinancialEntityRepository financialEntityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseEventRepository caseEventRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ScopeService scopeService;

    @Mock
    private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Clock clock;
    private FreezeRequestService freezeRequestService;

    private final Instant baseTime = Instant.parse("2026-09-20T10:00:00Z");
    private Bank hdfcBank;
    private Bank sbiBank;
    private User policeUser;
    private User bankUser;
    private User complainantUser;
    private Complaint complaint;
    private FinancialEntity targetEntity;
    private Jurisdiction jurisdiction;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        freezeRequestService = new FreezeRequestService(
                freezeRequestRepository,
                complaintRepository,
                financialEntityRepository,
                userRepository,
                caseEventRepository,
                notificationRepository,
                scopeService,
                auditService,
                objectMapper,
                clock
        );

        hdfcBank = new Bank("HDFC", "HDFC Bank", true);
        hdfcBank.setId(UUID.randomUUID());

        sbiBank = new Bank("SBI", "State Bank of India", true);
        sbiBank.setId(UUID.randomUUID());

        jurisdiction = new Jurisdiction();
        jurisdiction.setId(UUID.randomUUID());
        jurisdiction.setPath("/OD/KHORDHA/CYBER_PS/");

        policeUser = new User();
        policeUser.setId(UUID.randomUUID());
        policeUser.setEmail("inspector@police.gov.in");
        policeUser.setName("Police Officer");
        policeUser.setRole(UserRole.POLICE);
        policeUser.setStatus(UserStatus.ACTIVE);
        policeUser.setJurisdiction(jurisdiction);

        bankUser = new User();
        bankUser.setId(UUID.randomUUID());
        bankUser.setEmail("officer@hdfc.com");
        bankUser.setName("HDFC Officer");
        bankUser.setRole(UserRole.BANK_EMPLOYEE);
        bankUser.setStatus(UserStatus.ACTIVE);
        bankUser.setBank(hdfcBank);

        complainantUser = new User();
        complainantUser.setId(UUID.randomUUID());
        complainantUser.setEmail("victim@example.com");
        complainantUser.setName("Victim");
        complainantUser.setRole(UserRole.COMPLAINANT);
        complainantUser.setStatus(UserStatus.ACTIVE);

        complaint = new Complaint();
        complaint.setId(UUID.randomUUID());
        complaint.setHumanReference("CC-2026-000101");
        complaint.setComplainant(complainantUser);
        complaint.setJurisdiction(jurisdiction);
        complaint.setStatus(ComplaintStatus.UNDER_INVESTIGATION);

        targetEntity = new FinancialEntity("hash-mule-account", "enc-mule", hdfcBank, EntityType.ACCOUNT);
        targetEntity.setId(UUID.randomUUID());
    }

    @Test
    void createFreezeRequest_setsSevenDayDeadlineAndTransitionsCase() {
        UserPrincipal principal = UserPrincipal.fromUser(policeUser);
        CreateFreezeRequestDto dto = new CreateFreezeRequestDto(complaint.getId(), targetEntity.getId(), "Urgent freeze for mule account");

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));
        when(scopeService.isCaseInScope(any(), any(), any(), any())).thenReturn(true);
        when(financialEntityRepository.findById(targetEntity.getId())).thenReturn(Optional.of(targetEntity));
        when(userRepository.findById(policeUser.getId())).thenReturn(Optional.of(policeUser));
        when(freezeRequestRepository.save(any(FreezeRequest.class))).thenAnswer(inv -> {
            FreezeRequest f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        FreezeRequestResponseDto response = freezeRequestService.createFreezeRequest(dto, principal, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getDueAt()).isEqualTo(baseTime.plus(Duration.ofDays(7)));
        assertThat(response.getStatus()).isEqualTo(FreezeRequestStatus.OPEN);
        assertThat(response.getBankName()).isEqualTo("HDFC Bank");
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.FREEZE_REQUESTED);

        verify(caseEventRepository).save(any(CaseEvent.class));
        verify(notificationRepository).save(any());
        verify(auditService).log(eq(policeUser.getId()), eq("POLICE"), eq("FREEZE_REQUESTED"), eq("FREEZE_REQUEST"), any(), any(), any());
    }

    @Test
    void createFreezeRequest_throwsNotFound_whenOutOfScope() {
        UserPrincipal principal = UserPrincipal.fromUser(policeUser);
        CreateFreezeRequestDto dto = new CreateFreezeRequestDto(complaint.getId(), targetEntity.getId(), "Freeze");

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));
        when(scopeService.isCaseInScope(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> freezeRequestService.createFreezeRequest(dto, principal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Incident not found");

        verify(freezeRequestRepository, never()).save(any());
    }

    @Test
    void respondFreezeRequest_updatesStatusAndTransitionsComplaint() {
        FreezeRequest req = new FreezeRequest(
                complaint, targetEntity, hdfcBank, policeUser, baseTime, baseTime.plus(Duration.ofDays(7))
        );
        req.setId(UUID.randomUUID());
        complaint.setStatus(ComplaintStatus.FREEZE_REQUESTED);

        UserPrincipal bankPrincipal = UserPrincipal.fromUser(bankUser);
        RespondFreezeRequestDto dto = new RespondFreezeRequestDto(FreezeRequestStatus.FROZEN, "Account balance of INR 45,000 successfully frozen");

        when(freezeRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(userRepository.findById(bankUser.getId())).thenReturn(Optional.of(bankUser));
        when(freezeRequestRepository.save(any(FreezeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        FreezeRequestResponseDto response = freezeRequestService.respondFreezeRequest(req.getId(), dto, bankPrincipal, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(FreezeRequestStatus.FROZEN);
        assertThat(response.getResponseNote()).contains("frozen");
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.BANK_RESPONDED);

        verify(caseEventRepository).save(any(CaseEvent.class));
        verify(notificationRepository).save(any());
        verify(auditService).log(eq(bankUser.getId()), eq("BANK_EMPLOYEE"), eq("FREEZE_RESPONDED"), eq("FREEZE_REQUEST"), any(), any(), any());
    }

    @Test
    void respondFreezeRequest_throwsNotFound_whenDifferentBank() {
        FreezeRequest req = new FreezeRequest(
                complaint, targetEntity, hdfcBank, policeUser, baseTime, baseTime.plus(Duration.ofDays(7))
        );
        req.setId(UUID.randomUUID());

        // SBI officer tries to respond to HDFC freeze request
        User sbiUser = new User();
        sbiUser.setId(UUID.randomUUID());
        sbiUser.setRole(UserRole.BANK_EMPLOYEE);
        sbiUser.setStatus(UserStatus.ACTIVE);
        sbiUser.setBank(sbiBank);
        UserPrincipal sbiPrincipal = UserPrincipal.fromUser(sbiUser);

        RespondFreezeRequestDto dto = new RespondFreezeRequestDto(FreezeRequestStatus.FROZEN, "Frozen");

        when(freezeRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> freezeRequestService.respondFreezeRequest(req.getId(), dto, sbiPrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Freeze request not found");
    }

    @Test
    void respondFreezeRequest_throwsBadRequest_whenAlreadyProcessed() {
        FreezeRequest req = new FreezeRequest(
                complaint, targetEntity, hdfcBank, policeUser, baseTime, baseTime.plus(Duration.ofDays(7))
        );
        req.setId(UUID.randomUUID());
        req.setStatus(FreezeRequestStatus.FROZEN); // Already frozen

        UserPrincipal bankPrincipal = UserPrincipal.fromUser(bankUser);
        RespondFreezeRequestDto dto = new RespondFreezeRequestDto(FreezeRequestStatus.FROZEN, "Frozen again");

        when(freezeRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> freezeRequestService.respondFreezeRequest(req.getId(), dto, bankPrincipal, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already been processed");
    }

    @Test
    void checkSlaBreachesAndReminders_sendsDay5ReminderAndDay7EscalationOnce() {
        FreezeRequest req = new FreezeRequest(
                complaint, targetEntity, hdfcBank, policeUser, baseTime, baseTime.plus(Duration.ofDays(7))
        );
        req.setId(UUID.randomUUID());

        // 1. At Day 5: 2026-09-25T10:00:00Z
        Instant day5 = baseTime.plus(Duration.ofDays(5));
        when(freezeRequestRepository.findPendingReminders(any(Instant.class))).thenReturn(List.of(req));
        when(freezeRequestRepository.findOverdueEscalations(any(Instant.class))).thenReturn(Collections.emptyList());

        Map<String, Integer> resDay5 = freezeRequestService.checkSlaBreachesAndReminders(day5);
        assertThat(resDay5.get("remindersSent")).isEqualTo(1);
        assertThat(resDay5.get("escalationsTriggered")).isEqualTo(0);
        assertThat(req.getReminderSentAt()).isEqualTo(day5);
        verify(auditService).log(isNull(), eq("SYSTEM"), eq("FREEZE_REMINDER_SENT"), eq("FREEZE_REQUEST"), any(), any(), any());

        // 2. At Day 7: 2026-09-27T10:00:00Z
        Instant day7 = baseTime.plus(Duration.ofDays(7));
        when(freezeRequestRepository.findPendingReminders(any(Instant.class))).thenReturn(Collections.emptyList());
        when(freezeRequestRepository.findOverdueEscalations(any(Instant.class))).thenReturn(List.of(req));

        Map<String, Integer> resDay7 = freezeRequestService.checkSlaBreachesAndReminders(day7);
        assertThat(resDay7.get("remindersSent")).isEqualTo(0);
        assertThat(resDay7.get("escalationsTriggered")).isEqualTo(1);
        assertThat(req.getEscalatedAt()).isEqualTo(day7);
        verify(auditService).log(isNull(), eq("SYSTEM"), eq("FREEZE_ESCALATED"), eq("FREEZE_REQUEST"), any(), any(), any());
    }
}
