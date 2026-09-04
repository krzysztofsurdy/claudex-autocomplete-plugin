package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
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
import javax.swing.JLabel

class ClaudeAutocompleteConfigurable :
    BoundSearchableConfigurable("Claude Autocomplete", "claude.autocomplete.settings", "claude.autocomplete.settings") {

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
        val result = JLabel(" ")
        return panel {
            lateinit var providerCombo: Cell<ComboBox<String>>
            group("General") {
                row { checkBox("Enable inline completion").bindSelected(state::enabled) }
                row("Provider:") {
                    providerCombo = comboBox(listOf(CLAUDE_LABEL, CODEX_LABEL))
                        .bindItem(
                            { if (state.provider == "codex") CODEX_LABEL else CLAUDE_LABEL },
                            { state.provider = if (it == CODEX_LABEL) "codex" else "claude" },
                        )
                }
                row("Disabled languages:") {
                    textField().align(AlignX.FILL)
                        .bindText({ state.disabledLanguages.orEmpty() }, { state.disabledLanguages = it })
                        .comment("Comma separated language ids, e.g. Markdown, JSON")
                }
            }
            val isCodex = providerCombo.component.selectedValueIs(CODEX_LABEL)
            group("Claude Code") {
                row("Claude CLI path:") {
                    textField().align(AlignX.FILL).bindText({ state.claudePath.orEmpty() }, { state.claudePath = it })
                        .comment("Empty = auto-detect")
                }
                row("Model:") {
                    comboBox(listOf("haiku", "sonnet", "opus", "fable")).applyToComponent { isEditable = true }
                        .bindItem({ state.model }, { state.model = it.orEmpty().trim() })
                }
                row("Fallback model:") {
                    textField().bindText({ state.fallbackModel.orEmpty() }, { state.fallbackModel = it.trim() })
                }
                row("Effort:") {
                    comboBox(listOf("low", "medium", "high", "xhigh", "max"))
                        .bindItem({ state.effort }, { state.effort = it ?: "low" })
                }
                row { checkBox("Enable thinking").bindSelected(state::thinkingEnabled) }
                row("Thinking budget (tokens):") {
                    intTextField(0..100_000).bindIntText(state::thinkingBudgetTokens)
                }
                row {
                    comment("ANTHROPIC_API_KEY and CLAUDE_CODE_* environment variables are removed for requests so your Claude subscription login is used.")
                }
            }.visibleIf(isCodex.not())
            group("Codex") {
                row("Codex CLI path:") {
                    textField().align(AlignX.FILL).bindText({ state.codexPath.orEmpty() }, { state.codexPath = it })
                        .comment("Empty = auto-detect")
                }
                row("Model:") {
                    comboBox(listOf("gpt-5-codex-mini", "gpt-5-codex", "gpt-5")).applyToComponent { isEditable = true }
                        .bindItem({ state.codexModel }, { state.codexModel = it.orEmpty().trim() })
                }
                row("Reasoning effort:") {
                    comboBox(listOf("minimal", "low", "medium", "high"))
                        .bindItem({ state.codexReasoningEffort }, { state.codexReasoningEffort = it ?: "low" })
                }
                row { comment("Uses your ChatGPT subscription: run `codex login` in a terminal.") }
            }.visibleIf(isCodex)
            group("Request") {
                row { checkBox("Keep one CLI process alive (faster)").bindSelected(state::persistentProcess) }
                row("Custom prompt additions:") {
                    textArea().applyToComponent { rows = 5 }.align(AlignX.FILL)
                        .bindText({ state.customInstructions.orEmpty() }, { state.customInstructions = it })
                        .comment("Appended to the system prompt, e.g. Follow PSR-12, prefer readonly properties")
                }
            }
            group("Behaviour") {
                row("Debounce (ms):") { intTextField(0..2000).bindIntText(state::debounceMs) }
                row("Request timeout (ms):") { intTextField(500..120_000).bindIntText(state::requestTimeoutMs) }
                row { checkBox("Show loading indicator in editor while generating").bindSelected(state::showInlineLoadingIndicator) }
                row { checkBox("Show request state and usage in status bar").bindSelected(state::showUsageInStatusBar) }
                row("Multi-line mode:") {
                    comboBox(listOf("auto", "always", "never"))
                        .bindItem({ state.multilineMode }, { state.multilineMode = it ?: "auto" })
                }
                row("Max completion lines:") { intTextField(1..200).bindIntText(state::maxCompletionLines) }
            }
            group("Current file context") {
                lateinit var autoRadio: Cell<JBRadioButton>
                lateinit var aroundRadio: Cell<JBRadioButton>
                lateinit var linesField: Cell<JBTextField>
                buttonsGroup {
                    row {
                        autoRadio = radioButton("Whole file if it has at most", FileWindow.MODE_AUTO)
                        intTextField(1..1_000_000).bindIntText(state::wholeFileMaxLines).enabledIf(autoRadio.component.selected)
                        label("lines, otherwise")
                        linesField = intTextField(1..100_000).bindIntText(state::linesAroundCursor)
                        label("lines above and below the cursor")
                    }
                    row { radioButton("Always the whole file", FileWindow.MODE_WHOLE_FILE) }
                    row { aroundRadio = radioButton("Only the lines around the cursor (count above)", FileWindow.MODE_LINES_AROUND) }
                }.bind({ state.contextMode ?: FileWindow.MODE_AUTO }, { state.contextMode = it })
                linesField.enabledIf(autoRadio.component.selected or aroundRadio.component.selected)
                row { comment("Large contexts increase latency and token use. Characters beyond ${FileWindow.HARD_CAP_CHARS / 1000}k are always cut.") }
            }
            group("Additional context") {
                lateinit var tabs: Cell<JBCheckBox>
                lateinit var imports: Cell<JBCheckBox>
                row { tabs = checkBox("Include open tabs").bindSelected(state::includeOpenTabs) }
                row("Open tabs char budget:") { intTextField(0..200_000).bindIntText(state::maxOpenTabsChars).enabledIf(tabs.component.selected) }
                row { imports = checkBox("Include classes imported via `use` statements (PHP)").bindSelected(state::includeImportedClasses) }
                row("Imported classes char budget:") {
                    intTextField(0..200_000).bindIntText(state::maxImportedClassesChars).enabledIf(imports.component.selected)
                }
            }
            row {
                button("Test connection") { runTest(result) }
                cell(result)
            }
        }.also { panelRef = it }
    }

    private fun runTest(label: JLabel) {
        panelRef?.apply()
        label.text = "Testing..."
        val config = working.toBackendConfig()
        ApplicationManager.getApplication().executeOnPooledThread {
            val started = System.nanoTime()
            val outcome = runBlocking {
                BackendService.getInstance().ping(config).also { BackendService.getInstance().refreshUsage(config) }
            }
            val millis = (System.nanoTime() - started) / 1_000_000
            val text = when (outcome) {
                is CompletionResult.Success -> "OK in $millis ms: ${outcome.text.take(80).replace('\n', ' ')}"
                CompletionResult.Empty -> "OK in $millis ms (empty completion)"
                is CompletionResult.Failure -> "Failed (${outcome.kind.label()}): ${outcome.message.take(200)}"
            }
            ApplicationManager.getApplication().invokeLater({ label.text = text }, ModalityState.any())
        }
    }

    private companion object {
        const val CLAUDE_LABEL = "Claude Code"
        const val CODEX_LABEL = "Codex"
    }
}
