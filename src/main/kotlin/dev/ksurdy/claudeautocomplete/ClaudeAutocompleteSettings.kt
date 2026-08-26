package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.ClaudeConfig

@Service(Service.Level.APP)
@State(name = "ClaudeAutocompleteSettings", storages = [Storage("claude-autocomplete.xml")])
class ClaudeAutocompleteSettings : SimplePersistentStateComponent<ClaudeAutocompleteSettings.State>(State()) {

    class State : BaseState() {
        var enabled by property(true)
        var claudePath by string("")
        var model by string("haiku")
        var fallbackModel by string("")
        var effort by string("low")
        var thinkingEnabled by property(false)
        var thinkingBudgetTokens by property(1024)
        var debounceMs by property(250)
        var requestTimeoutMs by property(8000)
        var maxPrefixChars by property(6000)
        var maxSuffixChars by property(2000)
        var showUsageInStatusBar by property(true)
        var includeOpenTabs by property(true)
        var maxOpenTabsChars by property(6000)
        var multilineMode by string("auto")
        var maxCompletionLines by property(12)
        var persistentProcess by property(true)
        var customInstructions by string("")
        var disabledLanguages by string("")
    }

    fun toClaudeConfig(): ClaudeConfig = state.toClaudeConfig()

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
