package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
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
        persisted.copyFrom(working)
        StatusService.getInstance().refresh()
    }

    override fun createPanel(): DialogPanel {
        working.copyFrom(persisted)
        val state = working
        val result = JLabel(" ")
        return panel {
            group("General") {
                row { checkBox("Enable Claude autocomplete").bindSelected(state::enabled) }
                row("Claude CLI path:") {
                    textField().align(AlignX.FILL).bindText({ state.claudePath.orEmpty() }, { state.claudePath = it })
                        .comment("Empty = auto-detect")
                }
                row("Disabled languages:") {
                    textField().align(AlignX.FILL)
                        .bindText({ state.disabledLanguages.orEmpty() }, { state.disabledLanguages = it })
                        .comment("Comma separated language ids, e.g. Markdown, JSON")
                }
            }
            group("Model") {
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
                row { checkBox("Keep one CLI process alive (faster)").bindSelected(state::persistentProcess) }
                row {
                    comment("ANTHROPIC_API_KEY and CLAUDE_CODE_* environment variables are removed for requests so your Claude subscription login is used.")
                }
                row("Custom instructions:") {
                    textField().align(AlignX.FILL)
                        .bindText({ state.customInstructions.orEmpty() }, { state.customInstructions = it })
                        .comment("Appended to the system prompt, e.g. Follow PSR-12")
                }
            }
            group("Behaviour") {
                row("Debounce (ms):") { intTextField(0..2000).bindIntText(state::debounceMs) }
                row("Request timeout (ms):") { intTextField(500..120_000).bindIntText(state::requestTimeoutMs) }
                row("Max prefix chars:") { intTextField(100..200_000).bindIntText(state::maxPrefixChars) }
                row("Max suffix chars:") { intTextField(200..100_000).bindIntText(state::maxSuffixChars) }
                row { checkBox("Show request state and usage in status bar").bindSelected(state::showUsageInStatusBar) }
                row { checkBox("Include open tabs as context").bindSelected(state::includeOpenTabs) }
                row("Open tabs char budget:") { intTextField(0..200_000).bindIntText(state::maxOpenTabsChars) }
                row("Multi-line mode:") {
                    comboBox(listOf("auto", "always", "never"))
                        .bindItem({ state.multilineMode }, { state.multilineMode = it ?: "auto" })
                }
                row("Max completion lines:") { intTextField(1..200).bindIntText(state::maxCompletionLines) }
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
        val config = working.toClaudeConfig()
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
}
