package com.sih.dataservice.whatsapp.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mock/Sandbox implementation of TranslationService for development and testing.
 */
@Service
public class MockTranslationService implements TranslationService {

    private static final Logger log = LoggerFactory.getLogger(MockTranslationService.class);

    @Override
    public String translateToEnglish(String text, String sourceLanguage) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (sourceLanguage == null || "en".equalsIgnoreCase(sourceLanguage.trim())) {
            return text;
        }

        log.debug("MockTranslationService: Translating from lang={} length={}", sourceLanguage, text.length());
        // For testing/mock purposes, if not English, provide translated representation
        return "[EN Translated from " + sourceLanguage.toUpperCase() + "]: " + text;
    }
}
