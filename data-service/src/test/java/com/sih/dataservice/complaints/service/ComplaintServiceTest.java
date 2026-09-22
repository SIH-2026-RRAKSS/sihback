package com.sih.dataservice.complaints.service;

import com.sih.dataservice.audit.service.AuditService;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.auth.scope.ScopeService;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.complaints.dto.AccountRequestDto;
import com.sih.dataservice.complaints.dto.ComplaintResponseDto;
import com.sih.dataservice.complaints.dto.CreateComplaintRequest;
import com.sih.dataservice.complaints.entity.*;
import com.sih.dataservice.complaints.repository.*;
import com.sih.dataservice.notify.entity.Notification;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.repository.BankRepository;
import com.sih.dataservice.users.repository.JurisdictionRepository;
import com.sih.dataservice.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private ComplaintAccountRepository complaintAccountRepository;
    @Mock
    private FinancialEntityRepository financialEntityRepository;
    @Mock
    private CaseEventRepository caseEventRepository;
    @Mock
    private EvidenceRepository evidenceRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BankRepository bankRepository;
    @Mock
    private JurisdictionRepository jurisdictionRepository;
    @Mock
    private ReferenceNumberService referenceNumberService;
    @Mock
    private EvidenceStorageService evidenceStorageService;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private ScopeService scopeService;
    @Mock
    private AuditService auditService;

    private Clock fixedClock;
    private ComplaintService complaintService;

    private User complainant;
    private User otherUser;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
        complaintService = new ComplaintService(
                complaintRepository,
                complaintAccountRepository,
                financialEntityRepository,
                caseEventRepository,
                evidenceRepository,
                notificationRepository,
                userRepository,
                bankRepository,
                jurisdictionRepository,
                referenceNumberService,
                evidenceStorageService,
                cryptoService,
                scopeService,
                auditService,
                fixedClock
        );

        complainant = new User();
        complainant.setId(UUID.randomUUID());
        complainant.setEmail("victim@example.com");
        complainant.setRole(UserRole.COMPLAINANT);

        otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");
        otherUser.setRole(UserRole.COMPLAINANT);
    }

    @Test
    void createComplaintSuccessfullyWithAccountEncryption() {
        UserPrincipal principal = UserPrincipal.fromUser(complainant);

        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setFraudType("UPI_FRAUD");
        request.setAmount(new BigDecimal("15000.00"));
        request.setIncidentTime(Instant.now(fixedClock));
        request.setDescriptionOriginal("Sent money to fake seller");
        request.setChannel(ComplaintChannel.WEB);

        AccountRequestDto accDto = new AccountRequestDto(
                "9876543210@upi", EntityType.UPI, AccountRole.SUSPECT, null, null);
        request.setAccounts(List.of(accDto));

        when(userRepository.findById(complainant.getId())).thenReturn(Optional.of(complainant));
        when(referenceNumberService.generateReference()).thenReturn("CC-2026-000001");
        when(cryptoService.computeHmac("9876543210@upi")).thenReturn("mock-hmac-hash");
        when(cryptoService.encrypt("9876543210@upi")).thenReturn("mock-aes-encrypted");
        when(financialEntityRepository.findByAccountHash("mock-hmac-hash")).thenReturn(Optional.empty());
        when(financialEntityRepository.save(any(FinancialEntity.class))).thenAnswer(i -> i.getArgument(0));

        when(complaintRepository.save(any(Complaint.class))).thenAnswer(i -> {
            Complaint c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ComplaintResponseDto response = complaintService.createComplaint(request, principal, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getHumanReference()).isEqualTo("CC-2026-000001");
        assertThat(response.getStatus()).isEqualTo(PublicStatus.RECEIVED); // Complainant view!

        verify(cryptoService).computeHmac("9876543210@upi");
        verify(cryptoService).encrypt("9876543210@upi");
        verify(financialEntityRepository).save(any(FinancialEntity.class));
        verify(complaintAccountRepository).save(any(ComplaintAccount.class));
        verify(caseEventRepository).save(any(CaseEvent.class));
        verify(notificationRepository).save(any(Notification.class));
        verify(auditService).log(eq(complainant.getId()), eq("COMPLAINANT"), eq("FILE_COMPLAINT"),
                eq("COMPLAINT"), anyString(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void getComplainantComplaintByIdReturns404ForCrossUserAccess() {
        Complaint complaint = new Complaint();
        complaint.setId(UUID.randomUUID());
        complaint.setComplainant(otherUser);
        complaint.setStatus(ComplaintStatus.FILED);

        when(complaintRepository.findById(complaint.getId())).thenReturn(Optional.of(complaint));

        UserPrincipal principal = UserPrincipal.fromUser(complainant);

        // Accessing other user's complaint must throw 404 NOT_FOUND (never 403) per NFR-SEC-1
        assertThatThrownBy(() -> complaintService.getComplainantComplaintById(complaint.getId(), principal))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Complaint not found");
    }

    @Test
    void publicStatusMappingMatchesSpecification() {
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.FILED)).isEqualTo(PublicStatus.RECEIVED);
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.TRIAGED)).isEqualTo(PublicStatus.UNDER_REVIEW);
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.ASSIGNED)).isEqualTo(PublicStatus.UNDER_REVIEW);
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.UNDER_INVESTIGATION)).isEqualTo(PublicStatus.UNDER_REVIEW);
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.FREEZE_REQUESTED)).isEqualTo(PublicStatus.ACTION_TAKEN);
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.BANK_RESPONDED)).isEqualTo(PublicStatus.ACTION_TAKEN);
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.CLOSED_FRAUD)).isEqualTo(PublicStatus.CLOSED);
        assertThat(PublicStatus.fromInternalStatus(ComplaintStatus.CLOSED_NOT_FRAUD)).isEqualTo(PublicStatus.CLOSED);
    }
}
