package com.sih.dataservice.whatsapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.complaints.dto.ComplaintResponseDto;
import com.sih.dataservice.complaints.dto.CreateComplaintRequest;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintStatus;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.complaints.service.ComplaintService;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import com.sih.dataservice.whatsapp.entity.WhatsAppSession;
import com.sih.dataservice.whatsapp.entity.WhatsAppSessionState;
import com.sih.dataservice.whatsapp.gateway.MessagingGateway;
import com.sih.dataservice.whatsapp.i18n.MessageBundleService;
import com.sih.dataservice.whatsapp.repository.WhatsAppSessionRepository;
import com.sih.dataservice.whatsapp.translation.MockTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppBotServiceTest {

    @Mock
    private WhatsAppSessionRepository sessionRepository;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ComplaintService complaintService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private MessagingGateway messagingGateway;

    private MessageBundleService bundleService;
    private MockTranslationService translationService;
    private ObjectMapper objectMapper;
    private WhatsAppBotService botService;

    private final String rawPhone = "+919876543210";
    private final String phoneHash = "phone-hash-12345";
    private WhatsAppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        bundleService = new MessageBundleService();
        translationService = new MockTranslationService();
        objectMapper = new ObjectMapper();

        botService = new WhatsAppBotService(
                sessionRepository,
                complaintRepository,
                userRepository,
                complaintService,
                messagingGateway,
                bundleService,
                translationService,
                cryptoService,
                objectMapper
        );

        when(cryptoService.computeHmac(rawPhone)).thenReturn(phoneHash);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setName("Ramesh Kumar");
        testUser.setRole(UserRole.COMPLAINANT);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setPhoneHash(phoneHash);

        session = new WhatsAppSession(phoneHash);
        session.setUser(testUser);
        session.setState(WhatsAppSessionState.LANGUAGE);
        session.setLanguage("en");

        when(sessionRepository.findByPhoneHash(phoneHash)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.save(any(WhatsAppSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void fullConversationFlow_createsComplaintSuccessfully() {
        // Step 1: Send 'HI'
        String reply1 = botService.handleIncomingMessage(rawPhone, "HI");
        assertThat(reply1).contains("National Cybercrime Helpline");

        // Step 2: Choose English ('1') -> Transitions to FRAUD_TYPE
        String reply2 = botService.handleIncomingMessage(rawPhone, "1");
        assertThat(reply2).contains("What type of cyber fraud occurred");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.FRAUD_TYPE);

        // Step 3: Choose UPI_FRAUD ('1') -> Transitions to AMOUNT_DATE
        String reply3 = botService.handleIncomingMessage(rawPhone, "1");
        assertThat(reply3).contains("lost amount in INR and incident date");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.AMOUNT_DATE);

        // Step 4: Enter amount and date -> Transitions to ACCOUNT_OR_UTR
        String reply4 = botService.handleIncomingMessage(rawPhone, "35000 2026-09-22");
        assertThat(reply4).contains("suspect bank account");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.ACCOUNT_OR_UTR);

        // Step 5: Enter suspect account -> Transitions to DESCRIPTION
        String reply5 = botService.handleIncomingMessage(rawPhone, "fake-upi@okhdfc");
        assertThat(reply5).contains("brief description");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.DESCRIPTION);

        // Step 6: Enter description -> Transitions to EVIDENCE
        String reply6 = botService.handleIncomingMessage(rawPhone, "Clicked a phishing link on WhatsApp");
        assertThat(reply6).contains("screenshot or evidence document");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.EVIDENCE);

        // Step 7: Enter evidence ('NONE') -> Transitions to CONFIRM
        String reply7 = botService.handleIncomingMessage(rawPhone, "NONE");
        assertThat(reply7).contains("Review your complaint details")
                .contains("35000")
                .contains("fake-upi@okhdfc")
                .contains("Reply *YES* to submit");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.CONFIRM);

        // Mock complaintService response
        ComplaintResponseDto responseDto = new ComplaintResponseDto();
        responseDto.setId(UUID.randomUUID());
        responseDto.setHumanReference("CC-2026-000101");
        responseDto.setStatus(com.sih.dataservice.complaints.entity.PublicStatus.RECEIVED);
        when(complaintService.createComplaint(any(CreateComplaintRequest.class), any(UserPrincipal.class), anyString()))
                .thenReturn(responseDto);

        // Step 8: Confirm ('YES') -> Transitions to DONE
        String reply8 = botService.handleIncomingMessage(rawPhone, "YES");
        assertThat(reply8).contains("Complaint Filed Successfully")
                .contains("CC-2026-000101");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.DONE);

        verify(complaintService, times(1)).createComplaint(any(CreateComplaintRequest.class), any(UserPrincipal.class), anyString());
    }

    @Test
    void cancelCommand_resetsDraftAtAnyStage() {
        session.setState(WhatsAppSessionState.AMOUNT_DATE);
        session.setDraft("{\"fraudType\":\"UPI_FRAUD\"}");

        String reply = botService.handleIncomingMessage(rawPhone, "CANCEL");
        assertThat(reply).contains("cancelled");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.LANGUAGE);
        assertThat(session.getDraft()).isEqualTo("{}");
    }

    @Test
    void statusCommand_queriesComplaintWithoutAlteringSessionDraft() {
        session.setState(WhatsAppSessionState.ACCOUNT_OR_UTR);
        session.setDraft("{\"amount\":25000}");

        Complaint complaint = new Complaint();
        complaint.setHumanReference("CC-2026-000101");
        complaint.setStatus(ComplaintStatus.UNDER_INVESTIGATION);
        complaint.setUpdatedAt(Instant.now());

        when(complaintRepository.findByHumanReference("CC-2026-000101")).thenReturn(Optional.of(complaint));

        String reply = botService.handleIncomingMessage(rawPhone, "status CC-2026-000101");
        assertThat(reply).contains("CC-2026-000101")
                .contains("UNDER_REVIEW"); // Public status for UNDER_INVESTIGATION

        // Session state and draft remain completely unchanged
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.ACCOUNT_OR_UTR);
        assertThat(session.getDraft()).contains("25000");
    }

    @Test
    void statusCommand_notFound_returnsHelpfulMessage() {
        when(complaintRepository.findByHumanReference("CC-UNKNOWN")).thenReturn(Optional.empty());

        String reply = botService.handleIncomingMessage(rawPhone, "status CC-UNKNOWN");
        assertThat(reply).contains("No complaint found").contains("CC-UNKNOWN");
    }

    @Test
    void invalidInputInAmountDate_rePromptsWithHint() {
        session.setState(WhatsAppSessionState.AMOUNT_DATE);

        String reply = botService.handleIncomingMessage(rawPhone, "invalid-amount-format");
        assertThat(reply).contains("We did not understand that input")
                .contains("lost amount in INR");
        assertThat(session.getState()).isEqualTo(WhatsAppSessionState.AMOUNT_DATE);
    }
}
