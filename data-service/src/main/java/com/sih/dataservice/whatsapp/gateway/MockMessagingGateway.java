package com.sih.dataservice.whatsapp.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock/Sandbox implementation of MessagingGateway for testing and development.
 * Logs messages without exposing sensitive PII and records sent messages in-memory.
 */
@Component
public class MockMessagingGateway implements MessagingGateway {

    private static final Logger log = LoggerFactory.getLogger(MockMessagingGateway.class);

    private final List<SentMessageRecord> sentMessages = new CopyOnWriteArrayList<>();
    private volatile boolean simulateFailure = false;
    private volatile RuntimeException customFailureException = null;

    @Override
    public void sendMessage(String toPhoneNumber, String messageText) {
        if (simulateFailure) {
            log.warn("MockMessagingGateway: Simulated failure triggered for outbound message");
            throw customFailureException != null ? customFailureException : new RuntimeException("Simulated messaging gateway network failure");
        }

        String maskedPhone = maskPhoneNumber(toPhoneNumber);
        log.info("MockMessagingGateway [SEND]: to={}, textLength={}", maskedPhone, messageText != null ? messageText.length() : 0);
        sentMessages.add(new SentMessageRecord(toPhoneNumber, messageText, null, null, Instant.now()));
    }

    @Override
    public void sendTemplateMessage(String toPhoneNumber, String templateName, Map<String, String> parameters) {
        if (simulateFailure) {
            log.warn("MockMessagingGateway: Simulated failure triggered for outbound template message");
            throw customFailureException != null ? customFailureException : new RuntimeException("Simulated messaging gateway template failure");
        }

        String maskedPhone = maskPhoneNumber(toPhoneNumber);
        log.info("MockMessagingGateway [SEND_TEMPLATE]: to={}, template={}", maskedPhone, templateName);
        sentMessages.add(new SentMessageRecord(toPhoneNumber, null, templateName, parameters, Instant.now()));
    }

    public List<SentMessageRecord> getSentMessages() {
        return Collections.unmodifiableList(sentMessages);
    }

    public void clearSentMessages() {
        sentMessages.clear();
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void setCustomFailureException(RuntimeException customFailureException) {
        this.customFailureException = customFailureException;
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, 2) + "******" + phone.substring(phone.length() - 2);
    }

    public record SentMessageRecord(
            String toPhoneNumber,
            String messageText,
            String templateName,
            Map<String, String> parameters,
            Instant sentAt
    ) {}
}
