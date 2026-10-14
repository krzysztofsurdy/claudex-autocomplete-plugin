package dev.ksurdy.claudeautocomplete.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider
import dev.ksurdy.claudeautocomplete.ClaudexBundle
import dev.ksurdy.claudeautocomplete.ClaudexIcons
import javax.swing.JComponent
import javax.swing.JLabel

class SuggestionHintIdeTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private fun tooltip(): JComponent = ClaudeInlineCompletionProvider().providerPresentation.getTooltip(project)

    private fun labels(component: JComponent): List<JLabel> =
        listOfNotNull(component as? JLabel) + component.components.filterIsInstance<JComponent>().flatMap(::labels)

    private fun text(component: JComponent): String = labels(component).joinToString(" ") { it.text.orEmpty() }

    fun testTooltipShowsTheClaudexNameFromTheBundle() {
        assertTrue(text(tooltip()).contains(ClaudexBundle.message("hint.name")))
    }

    fun testTooltipNeverShowsTheClassNameOrTheProviderId() {
        val text = text(tooltip())
        assertFalse(text, text.contains("ClaudeInlineCompletionProvider"))
        assertFalse(text, text.contains(ClaudeInlineCompletionProvider.ID))
    }

    fun testTooltipCarriesTheClaudexIcon() {
        assertTrue(labels(tooltip()).any { it.icon === ClaudexIcons.Logo })
    }

    fun testTooltipNamesTheActiveProviderAndModel() {
        ClaudeAutocompleteSettings.getInstance().state.provider = "claude"
        ClaudeAutocompleteSettings.getInstance().state.model = "haiku"
        assertTrue(text(tooltip()).contains("Claude haiku"))
        ClaudeAutocompleteSettings.getInstance().state.provider = "codex"
        ClaudeAutocompleteSettings.getInstance().state.codexModel = "gpt-5.3-codex"
        assertTrue(text(tooltip()).contains("Codex gpt-5.3-codex"))
    }
}
