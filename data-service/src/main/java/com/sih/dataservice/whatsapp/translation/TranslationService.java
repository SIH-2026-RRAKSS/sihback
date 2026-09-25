package com.sih.dataservice.whatsapp.translation;

/**
 * Interface for translating free-text complaint descriptions to English (FR-WA-1, design.md Section 16).
 */
public interface TranslationService {

    /**
     * Translates input text to English.
     *
     * @param text           original text
     * @param sourceLanguage ISO language code (e.g., "hi", "ta", "mr")
     * @return English translated text
     */
    String translateToEnglish(String text, String sourceLanguage);
}
