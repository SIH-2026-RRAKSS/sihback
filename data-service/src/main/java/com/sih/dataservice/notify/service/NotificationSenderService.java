package com.sih.dataservice.notify.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.notify.entity.Notification;
import com.sih.dataservice.notify.entity.NotificationStatus;
import com.sih.dataservice.notify.repository.NotificationRepository;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.whatsapp.gateway.MessagingGateway;
import com.sih.dataservice.whatsapp.i18n.MessageBundleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for delivering queued notifications with retry mechanics (FR-WA-4, NFR-REL-1).
 */
@Service
public class NotificationSenderService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSenderService.class);
    public static final int MAX_RETRIES = 3;

    private final NotificationRepository notificationRepository;
    private final MessagingGateway messagingGateway;
    private final MessageBundleService bundleService;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public NotificationSenderService(
            NotificationRepository notificationRepository,
            MessagingGateway messagingGateway,
            MessageBundleService bundleService,
            CryptoService cryptoService,
            ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.messagingGateway = messagingGateway;
        this.bundleService = bundleService;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /**
     * Scans for QUEUED notifications and attempts delivery via the messaging gateway.
     * Retries failed notifications up to MAX_RETRIES times.
     */
    @Transactional
    public int processPendingNotifications() {
        List<Notification> pendingList = notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.QUEUED);
        if (pendingList.isEmpty()) {
            return 0;
        }

        log.debug("Processing {} pending notification(s)", pendingList.size());
        int processedCount = 0;

        for (Notification notification : pendingList) {
            processSingleNotification(notification);
            processedCount++;
        }

        return processedCount;
    }

    private void processSingleNotification(Notification notification) {
        User recipient = notification.getUser();
        if (recipient == null) {
            log.warn("Notification id={} has no recipient user; marking FAILED", notification.getId());
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);
            return;
        }

        String recipientPhone = null;
        if (recipient.getPhoneEncrypted() != null) {
            try {
                recipientPhone = cryptoService.decrypt(recipient.getPhoneEncrypted());
            } catch (Exception e) {
                log.error("Failed to decrypt phone number for notification id={}", notification.getId());
            }
        }

        if (recipientPhone == null || recipientPhone.isBlank()) {
            log.warn("Recipient user id={} has no valid decrypted phone number; marking FAILED", recipient.getId());
            notification.setStatus(NotificationStatus.FAILED);
            notificationRepository.save(notification);
            return;
        }

        try {
            String messageText = buildMessageText(notification);
            messagingGateway.sendMessage(recipientPhone, messageText);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Successfully delivered notification id={} to user={}", notification.getId(), recipient.getId());

        } catch (Exception e) {
            int retries = notification.getRetryCount() + 1;
            notification.setRetryCount(retries);

            if (retries >= MAX_RETRIES) {
                notification.setStatus(NotificationStatus.FAILED);
                log.error("Notification id={} exceeded max retries ({}); marked FAILED. Error: {}",
                        notification.getId(), MAX_RETRIES, e.getMessage());
            } else {
                log.warn("Failed to deliver notification id={} (retry {}/{}); leaving in QUEUED. Error: {}",
                        notification.getId(), retries, MAX_RETRIES, e.getMessage());
            }
        }

        notificationRepository.save(notification);
    }

    private String buildMessageText(Notification notification) {
        Map<String, Object> payload = parsePayload(notification.getPayload());
        String template = notification.getTemplate();
        String ref = (String) payload.getOrDefault("reference", "N/A");
        String status = (String) payload.getOrDefault("publicStatus", payload.getOrDefault("status", "UPDATED"));
        String detail = (String) payload.getOrDefault("detail", "");

        return switch (template) {
            case "COMPLAINT_FILED" -> bundleService.getMessage("en", "bot.done.message", ref);
            case "FREEZE_REQUEST_SUBMITTED" -> bundleService.getMessage("en", "notify.freeze.update", ref);
            case "BANK_RESPONSE_RECEIVED" -> bundleService.getMessage("en", "notify.bank.update", ref, status);
            default -> bundleService.getMessage("en", "notify.status.update", ref, status, detail);
        };
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank() || "{}".equals(payloadJson.trim())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<HashMap<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
