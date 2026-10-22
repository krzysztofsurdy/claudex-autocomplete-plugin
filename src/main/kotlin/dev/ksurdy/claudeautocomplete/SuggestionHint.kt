package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionProviderPresentation
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import javax.swing.JComponent

class SuggestionHint : InlineCompletionProviderPresentation {
    override fun getTooltip(project: Project?): JComponent = JBLabel(label(ClaudeAutocompleteSettings.getInstance().state), ClaudexIcons.Logo, JBLabel.LEADING)

    companion object {
        fun label(state: ClaudeAutocompleteSettings.State): String {
            val kind = state.providerKind()
            val model = when (kind) {
                ProviderKind.Claude -> state.model
                ProviderKind.Codex -> state.codexModel
            }.orEmpty()
            return if (model.isBlank()) ClaudexBundle.message("hint.name") else ClaudexBundle.message("hint.name.model", kind.displayName(), model)
        }
    }
}
