package com.sih.dataservice.whatsapp.entity;

/**
 * Conversation states for the guided WhatsApp filing bot (FR-WA-1, design.md Section 15).
 */
public enum WhatsAppSessionState {
    LANGUAGE,
    IDENTITY_LINK,
    FRAUD_TYPE,
    AMOUNT_DATE,
    ACCOUNT_OR_UTR,
    DESCRIPTION,
    EVIDENCE,
    CONFIRM,
    DONE
}
