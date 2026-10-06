package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.NamedColorUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.layout.not
import com.intellij.ui.layout.or
import com.intellij.ui.layout.selectedValueIs
import com.intellij.ui.layout.selected
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import kotlinx.coroutines.runBlocking
import javax.swing.JButton

class ClaudeAutocompleteConfigurable :
    BoundSearchableConfigurable(ClaudexBundle.message("settings.display.name"), SETTINGS_ID, SETTINGS_ID) {

    private val persisted get() = ClaudeAutocompleteSettings.getInstance().state
    private val working = ClaudeAutocompleteSettings.State()
    private var panelRef: DialogPanel? = null

    override fun reset() {
        working.copyFrom(persisted)
        super.reset()
    }

    override fun isModified(): Boolean = super.isModified() || working != persisted

    override fun apply() {
        super.apply()
        val providerBefore = persisted.providerKind()
        persisted.copyFrom(working)
        if (persisted.providerKind() != providerBefore) BackendService.getInstance().providerChanged()
        StatusService.getInstance().refresh()
    }

    override fun createPanel(): DialogPanel {
        working.copyFrom(persisted)
        val state = working
        val result = JBLabel(" ")
        return panel {
            lateinit var providerCombo: Cell<ComboBox<String>>
            group(ClaudexBundle.message("settings.group.general")) {
                row {
                    comment(ClaudexBundle.message("settings.general.comment"))
                }
                row {
                    checkBox(ClaudexBundle.message("settings.enable")).bindSelected(
                        { state.isActive },
                        { state.enabled = it; if (it) state.consentGiven = true },
                    )
                }
                row(ClaudexBundle.message("settings.provider")) {
                    providerCombo = comboBox(listOf(CLAUDE_LABEL, CODEX_LABEL))
                        .bindItem(
                            { if (state.provider == "codex") CODEX_LABEL else CLAUDE_LABEL },
                            { state.provider = if (it == CODEX_LABEL) "codex" else "claude" },
                        )
                }
                row(ClaudexBundle.message("settings.disabled.languages")) {
                    textField().align(AlignX.FILL)
                        .bindText({ state.disabledLanguages.orEmpty() }, { state.disabledLanguages = it })
                        .comment(ClaudexBundle.message("settings.disabled.languages.comment"))
                }
            }
            val isCodex = providerCombo.component.selectedValueIs(CODEX_LABEL)
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
                row { checkBox(ClaudexBundle.message("settings.thinking")).bindSelected(state::thinkingEnabled) }
                row(ClaudexBundle.message("settings.thinking.budget")) {
                    intTextField(0..100_000).bindIntText(state::thinkingBudgetTokens)
                }
                row {
                    comment(ClaudexBundle.message("settings.claude.env.comment"))
                }
            }.visibleIf(isCodex.not())
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
            }.visibleIf(isCodex)
            group(ClaudexBundle.message("settings.group.request")) {
                row {
                    checkBox(ClaudexBundle.message("settings.persistent")).bindSelected(state::persistentProcess)
                        .enabledIf(isCodex.not())
                }
                row(ClaudexBundle.message("settings.custom.instructions")) {
                    textArea().applyToComponent { rows = 5 }.align(AlignX.FILL)
                        .bindText({ state.customInstructions.orEmpty() }, { state.customInstructions = it })
                        .comment(ClaudexBundle.message("settings.custom.instructions.comment"))
                }
            }
            group(ClaudexBundle.message("settings.group.behavior")) {
                row(ClaudexBundle.message("settings.debounce")) { intTextField(0..2000).bindIntText(state::debounceMs) }
                row(ClaudexBundle.message("settings.timeout")) { intTextField(500..120_000).bindIntText(state::requestTimeoutMs) }
                row { checkBox(ClaudexBundle.message("settings.loading.indicator")).bindSelected(state::showInlineLoadingIndicator) }
                row { checkBox(ClaudexBundle.message("settings.status.usage")).bindSelected(state::showUsageInStatusBar) }
                row(ClaudexBundle.message("settings.multiline")) {
                    comboBox(listOf("auto", "always", "never"), optionRenderer())
                        .bindItem({ state.multilineMode }, { state.multilineMode = it ?: "auto" })
                }
                row(ClaudexBundle.message("settings.max.lines")) { intTextField(1..200).bindIntText(state::maxCompletionLines) }
            }
            group(ClaudexBundle.message("settings.group.context")) {
                lateinit var autoRadio: Cell<JBRadioButton>
                lateinit var aroundRadio: Cell<JBRadioButton>
                lateinit var linesField: Cell<JBTextField>
                buttonsGroup {
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
            }
            group(ClaudexBundle.message("settings.group.additional")) {
                lateinit var tabs: Cell<JBCheckBox>
                lateinit var imports: Cell<JBCheckBox>
                row { tabs = checkBox(ClaudexBundle.message("settings.include.tabs")).bindSelected(state::includeOpenTabs) }
                row(ClaudexBundle.message("settings.tabs.budget")) { intTextField(0..200_000).bindIntText(state::maxOpenTabsChars).enabledIf(tabs.component.selected) }
                row { imports = checkBox(ClaudexBundle.message("settings.include.imports")).bindSelected(state::includeImportedClasses) }
                row(ClaudexBundle.message("settings.imports.budget")) {
                    intTextField(0..200_000).bindIntText(state::maxImportedClassesChars).enabledIf(imports.component.selected)
                }
                row(ClaudexBundle.message("settings.excluded.patterns")) {
                    textField().align(AlignX.FILL)
                        .bindText({ state.excludedFilePatterns.orEmpty() }, { state.excludedFilePatterns = it })
                        .comment(ClaudexBundle.message("settings.excluded.patterns.comment"))
                }
            }
            row {
                button(ClaudexBundle.message("settings.test.connection")) { event -> runTest(result, event.source as JButton) }
                cell(result)
            }
        }.also { panelRef = it }
    }

    private fun optionRenderer() = SimpleListCellRenderer.create<String>("") { OptionLabels.display(it) }

    private fun runTest(label: JBLabel, button: JButton) {
        panelRef?.apply()
        button.isEnabled = false
        label.foreground = UIUtil.getLabelForeground()
        label.text = ClaudexBundle.message("settings.test.running")
        val config = working.toBackendConfig()
        ApplicationManager.getApplication().executeOnPooledThread {
            val started = System.nanoTime()
            val outcome = runBlocking {
                BackendService.getInstance().ping(config).also { BackendService.getInstance().refreshUsage(config) }
            }
            val millis = (System.nanoTime() - started) / 1_000_000
            val failure = outcome as? CompletionResult.Failure
            if (failure != null) LOG.warn("Test connection failed: kind=${failure.kind} message=${failure.message.take(MAX_LOGGED_MESSAGE)}")
            val text = when (outcome) {
                is CompletionResult.Success -> ClaudexBundle.message("settings.test.ok", millis, outcome.text.take(80).replace('\n', ' '))
                CompletionResult.Empty -> ClaudexBundle.message("settings.test.ok.empty", millis)
                is CompletionResult.Failure -> ClaudexBundle.message("settings.test.failed", outcome.kind.label(), outcome.message.take(200))
            }
            ApplicationManager.getApplication().invokeLater({
                label.foreground = if (failure != null) NamedColorUtil.getErrorForeground() else UIUtil.getLabelForeground()
                label.text = text
                button.isEnabled = true
            }, ModalityState.any())
        }
    }

    companion object {
        const val SETTINGS_ID = "dev.ksurdy.claudeautocomplete.settings"
        private const val MAX_LOGGED_MESSAGE = 300
        private val LOG = Logger.getInstance(ClaudeAutocompleteConfigurable::class.java)
        private const val CLAUDE_LABEL = "Claude Code"
        private const val CODEX_LABEL = "Codex"
    }
}
