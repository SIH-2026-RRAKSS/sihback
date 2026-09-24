package com.sih.dataservice.whatsapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.whatsapp.service.WhatsAppBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook controller for incoming WhatsApp messages and verification handshakes (FR-WA-5).
 * Validates HMAC-SHA256 signature and drops duplicate message IDs.
 */
@Tag(name = "WhatsApp Webhook", description = "Endpoints for WhatsApp Business webhook verification and inbound message events")
@RestController
@RequestMapping("/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final WhatsAppBotService botService;
    private final ObjectMapper objectMapper;
    private final String verifyToken;
    private final String webhookSecret;

    // In-memory message deduplication cache tracking messageId -> timestamp
    private final Map<String, Instant> processedMessageIds = new ConcurrentHashMap<>();

    public WhatsAppWebhookController(
            WhatsAppBotService botService,
            ObjectMapper objectMapper,
            @Value("${whatsapp.webhook.verify-token:sih_whatsapp_verify_token_2026}") String verifyToken,
            @Value("${whatsapp.webhook.secret:sih_whatsapp_webhook_secret_key_2026}") String webhookSecret) {
        this.botService = botService;
        this.objectMapper = objectMapper;
        this.verifyToken = verifyToken;
        this.webhookSecret = webhookSecret;
    }

    /**
     * Webhook verification challenge handshake (Meta Graph API standard).
     */
    @Operation(summary = "Verify WhatsApp webhook challenge handshake")
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        log.info("WhatsApp webhook challenge verification received mode={}, tokenPresent={}", mode, token != null);

        if ("subscribe".equalsIgnoreCase(mode) && verifyToken.equals(token)) {
            log.info("WhatsApp webhook handshake successfully verified");
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }

        log.warn("WhatsApp webhook handshake failed: token mismatch");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch");
    }

    /**
     * Inbound message webhook endpoint with HMAC-SHA256 signature verification.
     */
    @Operation(summary = "Handle inbound WhatsApp message webhook")
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody String rawBody) {

        // 1. Verify HMAC Signature (FR-WA-5)
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            if (!isValidSignature(rawBody, signatureHeader)) {
                log.warn("Rejected WhatsApp webhook: invalid HMAC-SHA256 signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "error", "message", "Invalid webhook signature"));
            }
        } else {
            // Reject missing signature if secret is active
            log.warn("Rejected WhatsApp webhook: missing X-Hub-Signature-256 header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Missing X-Hub-Signature-256 header"));
        }

        // 2. Parse payload and extract sender, message, and messageId
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            InboundMessage message = extractMessage(root);

            if (message == null || message.sender() == null || message.sender().isBlank()) {
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "No actionable message in payload"));
            }

            // 3. Message deduplication (FR-WA-5, design.md Section 15)
            if (message.messageId() != null && !message.messageId().isBlank()) {
                if (processedMessageIds.containsKey(message.messageId())) {
                    log.info("Ignoring duplicate WhatsApp message id={}", message.messageId());
                    return ResponseEntity.ok(Map.of("status", "duplicate_ignored", "messageId", message.messageId()));
                }
                processedMessageIds.put(message.messageId(), Instant.now());
                cleanupDeduplicationCache();
            }

            // 4. Process with Bot Service
            String reply = botService.handleIncomingMessage(message.sender(), message.text());
            return ResponseEntity.ok(Map.of("status", "processed", "reply", reply != null ? reply : ""));

        } catch (Exception e) {
            log.error("Failed to parse WhatsApp webhook body: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Invalid JSON payload"));
        }
    }

    private boolean isValidSignature(String payload, String signatureHeader) {
        try {
            String cleanSig = signatureHeader.startsWith("sha256=") ? signatureHeader.substring(7) : signatureHeader;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] computedHash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computedHash);

            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    cleanSig.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error validating HMAC signature: {}", e.getMessage());
            return false;
        }
    }

    private InboundMessage extractMessage(JsonNode root) {
        // Option A: Standard Meta Graph API format
        if (root.has("entry") && root.get("entry").isArray() && !root.get("entry").isEmpty()) {
            JsonNode entry = root.get("entry").get(0);
            if (entry.has("changes") && entry.get("changes").isArray() && !entry.get("changes").isEmpty()) {
                JsonNode value = entry.get("changes").get(0).get("value");
                if (value.has("messages") && value.get("messages").isArray() && !value.get("messages").isEmpty()) {
                    JsonNode msg = value.get("messages").get(0);
                    String from = msg.has("from") ? msg.get("from").asText() : null;
                    String id = msg.has("id") ? msg.get("id").asText() : null;
                    String text = "";
                    if (msg.has("text") && msg.get("text").has("body")) {
                        text = msg.get("text").get("body").asText();
                    }
                    return new InboundMessage(from, text, id);
                }
            }
        }

        // Option B: Simplified Direct / Sandbox JSON format
        String from = root.has("from") ? root.get("from").asText()
                : (root.has("sender") ? root.get("sender").asText() : null);

        String text = root.has("text") ? root.get("text").asText()
                : (root.has("message") ? root.get("message").asText()
                : (root.has("body") ? root.get("body").asText() : ""));

        String id = root.has("messageId") ? root.get("messageId").asText()
                : (root.has("id") ? root.get("id").asText() : null);

        return new InboundMessage(from, text, id);
    }

    private void cleanupDeduplicationCache() {
        if (processedMessageIds.size() > 5000) {
            Instant cutoff = Instant.now().minusSeconds(3600);
            processedMessageIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }
    }

    public record InboundMessage(String sender, String text, String messageId) {}
}
