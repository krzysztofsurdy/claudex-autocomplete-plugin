package dev.ksurdy.claudeautocomplete.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderChoiceTest {
    @Test
    fun storedNamesMapToChoices() {
        assertEquals(ProviderChoice.Claude, ProviderChoice.fromStored("claude"))
        assertEquals(ProviderChoice.Codex, ProviderChoice.fromStored("codex"))
    }

    @Test
    fun unknownStoredNamesFallBackToClaude() {
        assertEquals(ProviderChoice.Claude, ProviderChoice.fromStored(null))
        assertEquals(ProviderChoice.Claude, ProviderChoice.fromStored(""))
        assertEquals(ProviderChoice.Claude, ProviderChoice.fromStored("gemini"))
    }

    @Test
    fun labelsRoundTrip() {
        ProviderChoice.entries.forEach { assertEquals(it, ProviderChoice.fromLabel(it.label)) }
        assertEquals("Claude Code", ProviderChoice.Claude.label)
        assertEquals("Codex", ProviderChoice.Codex.label)
    }

    @Test
    fun storedNamesAreUnchanged() {
        assertEquals("claude", ProviderChoice.Claude.stored)
        assertEquals("codex", ProviderChoice.Codex.stored)
    }

    @Test
    fun onlyTheSelectedProviderShowsItsGroup() {
        assertTrue(ProviderChoice.Claude.showsClaudeGroup)
        assertFalse(ProviderChoice.Claude.showsCodexGroup)
        assertTrue(ProviderChoice.Codex.showsCodexGroup)
        assertFalse(ProviderChoice.Codex.showsClaudeGroup)
    }
}
