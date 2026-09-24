package com.sih.dataservice.notify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.notify.entity.Notification;
import com.sih.dataservice.notify.entity.NotificationChannel;
import com.sih.dataservice.notify.entity.NotificationStatus;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.whatsapp.gateway.MockMessagingGateway;
import com.sih.dataservice.whatsapp.i18n.MessageBundleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSenderServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CryptoService cryptoService;

    private MockMessagingGateway messagingGateway;
    private MessageBundleService bundleService;
    private ObjectMapper objectMapper;
    private NotificationSenderService senderService;

    private User recipient;
    private final String rawPhone = "+919876543210";
    private final String encryptedPhone = "enc-phone-base64";

    @BeforeEach
    void setUp() {
        messagingGateway = new MockMessagingGateway();
        bundleService = new MessageBundleService();
        objectMapper = new ObjectMapper();

        senderService = new NotificationSenderService(
                notificationRepository,
                messagingGateway,
                bundleService,
                cryptoService,
                objectMapper
        );

        recipient = new User();
        recipient.setId(UUID.randomUUID());
        recipient.setName("Citizen Ramesh");
        recipient.setPhoneEncrypted(encryptedPhone);

        lenient().when(cryptoService.decrypt(encryptedPhone)).thenReturn(rawPhone);
    }

    @Test
    void processPendingNotifications_success_marksStatusSent() {
        Notification notification = new Notification(
                recipient,
                NotificationChannel.WHATSAPP,
                "CASE_STATUS_UPDATE",
                "{\"reference\":\"CC-2026-000101\",\"publicStatus\":\"ACTION_TAKEN\",\"detail\":\"Bank freeze requested\"}"
        );

        when(notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.QUEUED))
                .thenReturn(List.of(notification));

        int processed = senderService.processPendingNotifications();
        assertThat(processed).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();

        assertThat(messagingGateway.getSentMessages()).hasSize(1);
        assertThat(messagingGateway.getSentMessages().get(0).toPhoneNumber()).isEqualTo(rawPhone);
        assertThat(messagingGateway.getSentMessages().get(0).messageText()).contains("CC-2026-000101");

        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void processPendingNotifications_failure_incrementsRetryAndStaysQueued() {
        Notification notification = new Notification(
                recipient,
                NotificationChannel.WHATSAPP,
                "CASE_STATUS_UPDATE",
                "{\"reference\":\"CC-2026-000101\",\"publicStatus\":\"UNDER_REVIEW\"}"
        );
        notification.setRetryCount(0);

        messagingGateway.setSimulateFailure(true);

        when(notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.QUEUED))
                .thenReturn(List.of(notification));

        senderService.processPendingNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(notification.getRetryCount()).isEqualTo(1);
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void processPendingNotifications_maxRetriesExceeded_marksFailed() {
        Notification notification = new Notification(
                recipient,
                NotificationChannel.WHATSAPP,
                "CASE_STATUS_UPDATE",
                "{\"reference\":\"CC-2026-000101\",\"publicStatus\":\"UNDER_REVIEW\"}"
        );
        // Already retried 2 times (next failure is 3rd = MAX_RETRIES)
        notification.setRetryCount(2);

        messagingGateway.setSimulateFailure(true);

        when(notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.QUEUED))
                .thenReturn(List.of(notification));

        senderService.processPendingNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getRetryCount()).isEqualTo(3);
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void processPendingNotifications_missingRecipientPhone_marksFailed() {
        recipient.setPhoneEncrypted(null);

        Notification notification = new Notification(
                recipient,
                NotificationChannel.WHATSAPP,
                "CASE_STATUS_UPDATE",
                "{\"reference\":\"CC-2026-000101\"}"
        );

        when(notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.QUEUED))
                .thenReturn(List.of(notification));

        senderService.processPendingNotifications();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(notificationRepository, times(1)).save(notification);
    }
}
