package dev.ksurdy.claudeautocomplete.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueIs
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudexBundle

internal fun Panel.providerGroups(state: ClaudeAutocompleteSettings.State, providerCombo: ComboBox<String>) {
    claudeGroup(state, providerCombo)
    codexGroup(state, providerCombo)
}

private fun Panel.claudeGroup(state: ClaudeAutocompleteSettings.State, providerCombo: ComboBox<String>) {
    group(ClaudexBundle.message("settings.group.claude")) {
        row(ClaudexBundle.message("settings.claude.path")) {
            textField().align(AlignX.FILL).bindText({ state.claudePath.orEmpty() }, { state.claudePath = it })
                .comment(ClaudexBundle.message("settings.path.comment"))
        }
        row(ClaudexBundle.message("settings.model")) {
            comboBox(listOf("haiku", "sonnet", "opus", "fable")).applyToComponent { isEditable = true }
                .bindItem({ state.model }, { state.model = it.orEmpty().trim() })
        }
        row(ClaudexBundle.message("settings.fallback.model")) {
            textField().bindText({ state.fallbackModel.orEmpty() }, { state.fallbackModel = it.trim() })
        }
        row(ClaudexBundle.message("settings.effort")) {
            comboBox(listOf("low", "medium", "high", "xhigh", "max"), optionRenderer())
                .bindItem({ state.effort }, { state.effort = it ?: "low" })
        }
        row {
            val thinking = checkBox(ClaudexBundle.message("settings.thinking")).bindSelected(state::thinkingEnabled)
            label(ClaudexBundle.message("settings.thinking.budget")).enabledIf(thinking.component.selected)
            intTextField(0..100_000).bindIntText(state::thinkingBudgetTokens).enabledIf(thinking.component.selected)
        }
        row { checkBox(ClaudexBundle.message("settings.persistent")).bindSelected(state::persistentProcess) }
        row { comment(ClaudexBundle.message("settings.claude.env.comment")) }
    }.visibleIf(providerCombo.selectedValueIs(ProviderChoice.Claude.label))
}

private fun Panel.codexGroup(state: ClaudeAutocompleteSettings.State, providerCombo: ComboBox<String>) {
    group(ClaudexBundle.message("settings.group.codex")) {
        row(ClaudexBundle.message("settings.codex.path")) {
            textField().align(AlignX.FILL).bindText({ state.codexPath.orEmpty() }, { state.codexPath = it })
                .comment(ClaudexBundle.message("settings.path.comment"))
        }
        row(ClaudexBundle.message("settings.model")) {
            comboBox(listOf("gpt-5.3-codex", "gpt-5.2", "gpt-5.1-codex-mini")).applyToComponent { isEditable = true }
                .bindItem({ state.codexModel }, { state.codexModel = it.orEmpty().trim() })
        }
        row(ClaudexBundle.message("settings.codex.reasoning")) {
            comboBox(listOf("none", "minimal", "low", "medium", "high"), optionRenderer())
                .bindItem({ state.codexReasoningEffort }, { state.codexReasoningEffort = it ?: "low" })
        }
        row { comment(ClaudexBundle.message("settings.codex.login.comment")) }
    }.visibleIf(providerCombo.selectedValueIs(ProviderChoice.Codex.label))
}
