package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsMappingTest {
    @Test
    fun defaultsToClaude() {
        assertEquals(ProviderKind.Claude, ClaudeAutocompleteSettings.State().providerKind())
    }

    @Test
    fun mapsCodexSettingsToBackendConfig() {
        val state = ClaudeAutocompleteSettings.State().apply {
            provider = "codex"
            codexPath = "/usr/local/bin/codex"
            codexModel = "gpt-x"
            codexReasoningEffort = "high"
            requestTimeoutMs = 4000
            persistentProcess = false
            customInstructions = "PSR-12"
        }
        val config = state.toBackendConfig()
        assertEquals(ProviderKind.Codex, config.provider)
        assertEquals("/usr/local/bin/codex", config.codex.codexPath)
        assertEquals("gpt-x", config.codex.model)
        assertEquals("high", config.codex.reasoningEffort)
        assertEquals(4000, config.codex.requestTimeoutMs)
        assertEquals(false, config.codex.persistentProcess)
        assertEquals("PSR-12", config.codex.customInstructions)
        assertEquals("haiku", config.claude.model)
    }
}

class SettingsConsentTest {
    @Test
    fun inactiveUntilConsentGiven() {
        val state = ClaudeAutocompleteSettings.State()
        kotlin.test.assertFalse(state.isActive)
        state.consentGiven = true
        kotlin.test.assertTrue(state.isActive)
        state.enabled = false
        kotlin.test.assertFalse(state.isActive)
    }

    @Test
    fun promptsOnlyWhileUndecided() {
        val state = ClaudeAutocompleteSettings.State()
        kotlin.test.assertTrue(state.needsConsentPrompt)
        state.consentDeclined = true
        kotlin.test.assertFalse(state.needsConsentPrompt)
    }

    @Test
    fun grantingConsentClearsDecline() {
        val settings = ClaudeAutocompleteSettings()
        settings.declineConsent()
        kotlin.test.assertTrue(settings.state.consentDeclined)
        settings.grantConsent()
        kotlin.test.assertFalse(settings.state.consentDeclined)
        kotlin.test.assertTrue(settings.isActive)
    }

    @Test
    fun excludedPatternsDefaultToSensitiveDefaults() {
        kotlin.test.assertEquals(SensitiveFiles.DEFAULT_PATTERNS, ClaudeAutocompleteSettings.State().excludedFilePatterns)
    }
}
