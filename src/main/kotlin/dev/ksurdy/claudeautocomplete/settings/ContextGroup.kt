package dev.ksurdy.claudeautocomplete.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.layout.or
import com.intellij.ui.layout.selected
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudexBundle
import dev.ksurdy.claudeautocomplete.FileWindow
import kotlin.reflect.KMutableProperty0

internal fun Panel.contextGroup(state: ClaudeAutocompleteSettings.State) {
    group(ClaudexBundle.message("settings.group.context")) {
        lateinit var autoRadio: Cell<JBRadioButton>
        lateinit var aroundRadio: Cell<JBRadioButton>
        lateinit var linesField: Cell<JBTextField>
        buttonsGroup(ClaudexBundle.message("settings.context.current.file")) {
            row {
                autoRadio = radioButton(ClaudexBundle.message("settings.context.auto"), FileWindow.MODE_AUTO)
                intTextField(1..1_000_000).bindIntText(state::wholeFileMaxLines).enabledIf(autoRadio.component.selected)
                label(ClaudexBundle.message("settings.context.auto.lines"))
                linesField = intTextField(1..100_000).bindIntText(state::linesAroundCursor)
                label(ClaudexBundle.message("settings.context.auto.around"))
            }
            row { radioButton(ClaudexBundle.message("settings.context.whole"), FileWindow.MODE_WHOLE_FILE) }
            row { aroundRadio = radioButton(ClaudexBundle.message("settings.context.around"), FileWindow.MODE_LINES_AROUND) }
        }.bind({ state.contextMode ?: FileWindow.MODE_AUTO }, { state.contextMode = it })
        linesField.enabledIf(autoRadio.component.selected or aroundRadio.component.selected)
        row { comment(ClaudexBundle.message("settings.context.comment", FileWindow.HARD_CAP_CHARS / 1000)) }
        row {
            budgetedCheckBox(ClaudexBundle.message("settings.include.tabs"), state::includeOpenTabs, state::maxOpenTabsChars)
        }
        row {
            budgetedCheckBox(ClaudexBundle.message("settings.include.imports"), state::includeImportedClasses, state::maxImportedClassesChars)
        }
    }
}

private fun Row.budgetedCheckBox(
    text: String,
    included: KMutableProperty0<Boolean>,
    budget: KMutableProperty0<Int>,
) {
    val box: Cell<JBCheckBox> = checkBox(text).bindSelected(included)
    label(ClaudexBundle.message("settings.budget")).enabledIf(box.component.selected)
    intTextField(0..200_000).bindIntText(budget).enabledIf(box.component.selected)
}
