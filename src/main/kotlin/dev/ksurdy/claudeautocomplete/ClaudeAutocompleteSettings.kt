package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.ClaudeConfig
import dev.ksurdy.claudeautocomplete.backend.CodexConfig
import dev.ksurdy.claudeautocomplete.backend.ProviderKind

@Service(Service.Level.APP)
@State(name = "ClaudeAutocompleteSettings", storages = [Storage("claude-autocomplete.xml")])
class ClaudeAutocompleteSettings : SimplePersistentStateComponent<ClaudeAutocompleteSettings.State>(State()) {

    class State : BaseState() {
        var enabled by property(true)
        var provider by string("claude")
        var claudePath by string("")
        var codexPath by string("")
        var codexModel by string("gpt-5.3-codex")
        var codexReasoningEffort by string("low")
        var model by string("haiku")
        var fallbackModel by string("")
        var effort by string("low")
        var thinkingEnabled by property(false)
        var thinkingBudgetTokens by property(1024)
        var debounceMs by property(250)
        var requestTimeoutMs by property(8000)
        var contextMode by string(FileWindow.MODE_AUTO)
        var wholeFileMaxLines by property(1000)
        var linesAroundCursor by property(150)
        var showInlineLoadingIndicator by property(true)
        var showUsageInStatusBar by property(true)
        var includeOpenTabs by property(true)
        var maxOpenTabsChars by property(6000)
        var includeImportedClasses by property(true)
        var maxImportedClassesChars by property(8000)
        var multilineMode by string("auto")
        var maxCompletionLines by property(12)
        var persistentProcess by property(true)
        var customInstructions by string("")
        var disabledLanguages by string("")
    }

    fun toClaudeConfig(): ClaudeConfig = state.toClaudeConfig()

    fun toBackendConfig(): BackendConfig = state.toBackendConfig()

    companion object {
        fun getInstance(): ClaudeAutocompleteSettings = service()
    }
}

fun ClaudeAutocompleteSettings.State.toClaudeConfig(): ClaudeConfig = ClaudeConfig(
    claudePath = claudePath.orEmpty(),
    model = model.orEmpty(),
    fallbackModel = fallbackModel.orEmpty(),
    effort = effort.orEmpty(),
    thinkingEnabled = thinkingEnabled,
    thinkingBudgetTokens = thinkingBudgetTokens,
    requestTimeoutMs = requestTimeoutMs,
    persistentProcess = persistentProcess,
    customInstructions = customInstructions.orEmpty(),
)

fun ClaudeAutocompleteSettings.State.providerKind(): ProviderKind =
    if (provider == "codex") ProviderKind.Codex else ProviderKind.Claude

fun ClaudeAutocompleteSettings.State.toCodexConfig(): CodexConfig = CodexConfig(
    codexPath = codexPath.orEmpty(),
    model = codexModel.orEmpty(),
    reasoningEffort = codexReasoningEffort.orEmpty(),
    requestTimeoutMs = requestTimeoutMs,
    persistentProcess = persistentProcess,
    customInstructions = customInstructions.orEmpty(),
)

fun ClaudeAutocompleteSettings.State.toBackendConfig(): BackendConfig =
    BackendConfig(providerKind(), toClaudeConfig(), toCodexConfig())

fun ProviderKind.displayName(): String = when (this) {
    ProviderKind.Claude -> "Claude"
    ProviderKind.Codex -> "Codex"
}

fun BackendConfig.activeModel(): String = when (provider) {
    ProviderKind.Claude -> claude.model
    ProviderKind.Codex -> codex.model
}
