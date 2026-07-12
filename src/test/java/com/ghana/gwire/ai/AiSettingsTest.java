package com.ghana.gwire.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSettingsTest {

    @Test
    void loadDoesNotThrowWithoutEnv() {
        AiSettings settings = assertDoesNotThrow(AiSettings::load);
        assertNotNull(settings);
        assertNotNull(settings.provider());
        assertNotNull(settings.baseUrl());
        assertNotNull(settings.model());
        // Without a configured key, LLM should not be available
        // (env may set a key in some CI; still must not throw)
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            assertFalse(settings.isLlmAvailable());
        }
    }

    @Test
    void noneProviderNotAvailableEvenWithKeyShape() {
        AiSettings s = new AiSettings(AiSettings.Provider.NONE, "sk-test", null, null, false);
        assertFalse(s.isLlmAvailable());
    }

    @Test
    void openAiCompatWithKeyIsAvailable() {
        AiSettings s = new AiSettings(
                AiSettings.Provider.OPENAI_COMPAT,
                "sk-test-key",
                AiSettings.DEFAULT_OPENAI_BASE,
                "gpt-4o-mini",
                true
        );
        assertTrue(s.isLlmAvailable());
        assertTrue(s.maskedKey().contains("…") || s.maskedKey().equals("****"));
        assertFalse(s.maskedKey().contains("sk-test-key"));
    }

    @Test
    void blankKeyNotAvailable() {
        AiSettings s = new AiSettings(
                AiSettings.Provider.OPENAI_COMPAT,
                "  ",
                AiSettings.DEFAULT_XAI_BASE,
                "grok-beta",
                true
        );
        assertFalse(s.isLlmAvailable());
    }
}
