package dev.ksurdy.claudeautocomplete.backend

import java.util.concurrent.CopyOnWriteArrayList

class CompletionRouter(
    private val claude: ClaudeCliBackend = ClaudeCliBackend(),
    private val codex: CodexCliBackend = CodexCliBackend(),
) {
    private val listeners = CopyOnWriteArrayList<(UsageLimits) -> Unit>()

    @Volatile
    private var active: ProviderKind = ProviderKind.Claude

    init {
        claude.addUsageListener(::forward)
        codex.addUsageListener(::forward)
    }

    val lastUsage: UsageLimits?
        get() = when (active) {
            ProviderKind.Claude -> claude.lastUsage
            ProviderKind.Codex -> codex.lastUsage
        }

    suspend fun complete(context: CompletionContext, config: BackendConfig): CompletionResult {
        activate(config.provider)
        return when (config.provider) {
            ProviderKind.Claude -> claude.complete(context, config.claude)
            ProviderKind.Codex -> codex.complete(context, config.codex)
        }
    }

    suspend fun refreshUsage(config: BackendConfig): UsageLimits? {
        activate(config.provider)
        return when (config.provider) {
            ProviderKind.Claude -> claude.refreshUsage(config.claude)
            ProviderKind.Codex -> codex.refreshUsage(config.codex)
        }
    }

    fun addUsageListener(listener: (UsageLimits) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun shutdown() {
        claude.shutdown()
    }

    @Synchronized
    private fun activate(provider: ProviderKind) {
        if (active == provider) return
        if (provider == ProviderKind.Codex) claude.shutdown()
        active = provider
    }

    private fun forward(limits: UsageLimits) {
        for (listener in listeners) {
            try {
                listener(limits)
            } catch (_: RuntimeException) {
            }
        }
    }
}
