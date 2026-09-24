package com.sih.dataservice.whatsapp.gateway;

import java.util.Map;

/**
 * Gateway interface for sending outbound WhatsApp / SMS messages (FR-WA-5).
 */
public interface MessagingGateway {

    /**
     * Sends a plain text message to the specified recipient phone number.
     *
     * @param toPhoneNumber recipient phone number in E.164 or national format
     * @param messageText   text message body
     */
    void sendMessage(String toPhoneNumber, String messageText);

    /**
     * Sends a pre-approved template message to the specified recipient phone number.
     *
     * @param toPhoneNumber recipient phone number
     * @param templateName  registered template identifier
     * @param parameters    template variable substitutions
     */
    void sendTemplateMessage(String toPhoneNumber, String templateName, Map<String, String> parameters);
}
