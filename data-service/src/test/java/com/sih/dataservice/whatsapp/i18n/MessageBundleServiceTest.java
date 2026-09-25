package com.sih.dataservice.whatsapp.i18n;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBundleServiceTest {

    private MessageBundleService bundleService;

    @BeforeEach
    void setUp() {
        bundleService = new MessageBundleService();
    }

    @Test
    void getMessage_englishDefault() {
        String msg = bundleService.getMessage("en", "bot.welcome");
        assertThat(msg).contains("National Cybercrime Helpline");
    }

    @Test
    void getMessage_hindiTranslation() {
        String msg = bundleService.getMessage("hi", "bot.welcome");
        assertThat(msg).contains("\u0938\u093e\u0907\u092c\u0930");
    }

    @Test
    void getMessage_fallbackToEnglishWhenKeyMissingInRegional() {
        // If a regional bundle doesn't specify a key, it must fall back to English (NFR-I18N-1)
        String msg = bundleService.getMessage("kn", "notify.status.update", "CC-101", "TRIAGED", "Reviewing");
        assertThat(msg).contains("CC-101").contains("TRIAGED");
    }

    @Test
    void getMessage_withParameters() {
        String msg = bundleService.getMessage("en", "bot.done.message", "CC-2026-000101");
        assertThat(msg).contains("CC-2026-000101");
    }

    @Test
    void normalizeLanguage() {
        assertThat(bundleService.normalizeLanguage("2")).isEqualTo("hi");
        assertThat(bundleService.normalizeLanguage("Hindi")).isEqualTo("hi");
        assertThat(bundleService.normalizeLanguage("TAMIL")).isEqualTo("ta");
        assertThat(bundleService.normalizeLanguage("french")).isEqualTo("en");
    }
}
