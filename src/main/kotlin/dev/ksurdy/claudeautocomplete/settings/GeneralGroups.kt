package dev.ksurdy.claudeautocomplete.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudexBundle
import dev.ksurdy.claudeautocomplete.OptionLabels
import dev.ksurdy.claudeautocomplete.isActive
import javax.swing.JButton

internal fun Panel.generalGroup(
    state: ClaudeAutocompleteSettings.State,
    testResult: JBLabel,
    onTest: (JButton) -> Unit,
): ComboBox<String> {
    lateinit var providerCombo: Cell<ComboBox<String>>
    group(ClaudexBundle.message("settings.group.general")) {
        row { comment(ClaudexBundle.message("settings.general.comment")) }
        row {
            checkBox(ClaudexBundle.message("settings.enable")).bindSelected(
                { state.isActive },
                { state.enabled = it; if (it) state.consentGiven = true },
            )
        }
        row(ClaudexBundle.message("settings.provider")) {
            providerCombo = comboBox(ProviderChoice.entries.map { it.label }).bindItem(
                { ProviderChoice.fromStored(state.provider).label },
                { state.provider = ProviderChoice.fromLabel(it).stored },
            )
            button(ClaudexBundle.message("settings.test.connection")) { onTest(it.source as JButton) }
            cell(testResult)
        }
        row(ClaudexBundle.message("settings.disabled.languages")) {
            textField().align(AlignX.FILL)
                .bindText({ state.disabledLanguages.orEmpty() }, { state.disabledLanguages = it })
                .comment(ClaudexBundle.message("settings.disabled.languages.comment"))
        }
        row(ClaudexBundle.message("settings.excluded.patterns")) {
            textField().align(AlignX.FILL)
                .bindText({ state.excludedFilePatterns.orEmpty() }, { state.excludedFilePatterns = it })
                .comment(ClaudexBundle.message("settings.excluded.patterns.comment"))
        }
    }
    return providerCombo.component
}

internal fun Panel.inlineSuggestionsGroup(state: ClaudeAutocompleteSettings.State) {
    group(ClaudexBundle.message("settings.group.inline")) {
        row(ClaudexBundle.message("settings.delay")) { intTextField(0..2000).bindIntText(state::debounceMs) }
        row(ClaudexBundle.message("settings.timeout")) { intTextField(500..120_000).bindIntText(state::requestTimeoutMs) }
        row(ClaudexBundle.message("settings.multiline")) {
            comboBox(listOf("auto", "always", "never"), optionRenderer())
                .bindItem({ state.multilineMode }, { state.multilineMode = it ?: "auto" })
            label(ClaudexBundle.message("settings.max.lines"))
            intTextField(1..200).bindIntText(state::maxCompletionLines)
        }
        row { checkBox(ClaudexBundle.message("settings.lookup.open")).bindSelected(state::suggestWhileLookupOpen) }
        row {
            checkBox(ClaudexBundle.message("settings.prefetch")).bindSelected(state::prefetchAfterAccept)
                .comment(ClaudexBundle.message("settings.prefetch.comment"))
        }
        row { checkBox(ClaudexBundle.message("settings.loading.indicator")).bindSelected(state::showInlineLoadingIndicator) }
    }
}

internal fun Panel.nextEditGroup(state: ClaudeAutocompleteSettings.State) {
    group(ClaudexBundle.message("settings.group.next.edit")) {
        row {
            checkBox(ClaudexBundle.message("settings.next.edit")).bindSelected(state::nextEditSuggestions)
                .comment(ClaudexBundle.message("settings.next.edit.comment"))
        }
    }
}

internal fun Panel.advancedGroup(state: ClaudeAutocompleteSettings.State) {
    collapsibleGroup(ClaudexBundle.message("settings.group.advanced")) {
        row(ClaudexBundle.message("settings.custom.instructions")) {
            textArea().applyToComponent { rows = 5 }.align(AlignX.FILL)
                .bindText({ state.customInstructions.orEmpty() }, { state.customInstructions = it })
                .comment(ClaudexBundle.message("settings.custom.instructions.comment"))
        }
    }
}

internal fun optionRenderer() = SimpleListCellRenderer.create<String>("") { OptionLabels.display(it) }
