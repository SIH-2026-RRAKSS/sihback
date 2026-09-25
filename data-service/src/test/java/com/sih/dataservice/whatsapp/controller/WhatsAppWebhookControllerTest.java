package com.sih.dataservice.whatsapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.whatsapp.service.WhatsAppBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock
    private WhatsAppBotService botService;

    private final String verifyToken = "test_verify_token_123";
    private final String webhookSecret = "test_webhook_secret_key_456";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                botService,
                objectMapper,
                verifyToken,
                webhookSecret
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void verifyWebhook_validChallenge_returnsChallengeString() throws Exception {
        mockMvc.perform(get("/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", verifyToken)
                        .param("hub.challenge", "challenge_random_token_999"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_random_token_999"));
    }

    @Test
    void verifyWebhook_invalidToken_returnsForbidden() throws Exception {
        mockMvc.perform(get("/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong_token")
                        .param("hub.challenge", "challenge_random_token_999"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handleWebhook_missingSignature_returnsUnauthorized() throws Exception {
        String body = "{\"from\":\"+919876543210\",\"text\":\"hi\",\"messageId\":\"msg-001\"}";

        mockMvc.perform(post("/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(botService, never()).handleIncomingMessage(anyString(), anyString());
    }

    @Test
    void handleWebhook_invalidSignature_returnsUnauthorized() throws Exception {
        String body = "{\"from\":\"+919876543210\",\"text\":\"hi\",\"messageId\":\"msg-001\"}";

        mockMvc.perform(post("/whatsapp/webhook")
                        .header("X-Hub-Signature-256", "sha256=invalidhexsignature123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(botService, never()).handleIncomingMessage(anyString(), anyString());
    }

    @Test
    void handleWebhook_validSignature_processesMessage() throws Exception {
        String body = "{\"from\":\"+919876543210\",\"text\":\"hi\",\"messageId\":\"msg-001\"}";
        String sig = calculateHmac(body, webhookSecret);

        when(botService.handleIncomingMessage("+919876543210", "hi"))
                .thenReturn("Welcome to Cybercrime Helpline");

        mockMvc.perform(post("/whatsapp/webhook")
                        .header("X-Hub-Signature-256", "sha256=" + sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"))
                .andExpect(jsonPath("$.reply").value("Welcome to Cybercrime Helpline"));

        verify(botService, times(1)).handleIncomingMessage("+919876543210", "hi");
    }

    @Test
    void handleWebhook_duplicateMessageId_isIgnored() throws Exception {
        String body = "{\"from\":\"+919876543210\",\"text\":\"hi\",\"messageId\":\"dup-msg-999\"}";
        String sig = calculateHmac(body, webhookSecret);

        when(botService.handleIncomingMessage("+919876543210", "hi"))
                .thenReturn("Welcome to Cybercrime Helpline");

        // First delivery: processes message
        mockMvc.perform(post("/whatsapp/webhook")
                        .header("X-Hub-Signature-256", "sha256=" + sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));

        // Second delivery of same messageId: duplicate ignored
        mockMvc.perform(post("/whatsapp/webhook")
                        .header("X-Hub-Signature-256", "sha256=" + sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate_ignored"));

        // Bot service was called only once despite duplicate webhook delivery
        verify(botService, times(1)).handleIncomingMessage("+919876543210", "hi");
    }

    private String calculateHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
