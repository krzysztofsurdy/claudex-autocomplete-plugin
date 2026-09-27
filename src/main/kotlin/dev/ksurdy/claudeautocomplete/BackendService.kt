package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.CompletionRouter
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import dev.ksurdy.claudeautocomplete.backend.UsageLimits
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class BackendService : Disposable {
    private val router = CompletionRouter().also { router ->
        router.addUsageListener { limits ->
            if (limits.provider == ClaudeAutocompleteSettings.getInstance().state.providerKind()) {
                StatusService.getInstance().updateUsage(limits)
            }
        }
    }

    @Volatile
    var engine: CompletionEngine = RouterEngine(router, onCompleted = ::refreshUsageIfStale)

    private val refreshing = AtomicBoolean(false)

    @Volatile
    private var lastRefreshAttempt: Instant? = null

    fun providerChanged() {
        val status = StatusService.getInstance()
        status.clearUsage()
        status.blockUntil(null)
        status.update(ClaudeStatus.Ready)
        lastRefreshAttempt = null
    }

    suspend fun refreshUsage(config: BackendConfig): UsageLimits? {
        lastRefreshAttempt = Instant.now()
        val limits = engine.refreshUsage(config) ?: return null
        val status = StatusService.getInstance()
        status.updateUsage(limits)
        if (limits.status == "rejected") {
            status.update(ClaudeStatus.LimitReached(status.registerRateLimit(Instant.now())))
        }
        return limits
    }

    fun refreshUsageIfStale() {
        val settings = ClaudeAutocompleteSettings.getInstance()
        if (!settings.isActive) return
        val status = StatusService.getInstance()
        if (!StatusFormatter.usageIsStale(status.usage, lastRefreshAttempt, Instant.now())) return
        if (!refreshing.compareAndSet(false, true)) return
        val config = settings.toBackendConfig()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                runBlocking { refreshUsage(config) }
            } finally {
                refreshing.set(false)
            }
        }
    }

    suspend fun ping(config: BackendConfig): CompletionResult {
        val result = pingBackend(config)
        val status = StatusService.getInstance()
        when {
            result is CompletionResult.Failure && result.kind == FailureKind.RateLimited ->
                status.update(ClaudeStatus.LimitReached(status.registerRateLimit(Instant.now())))
            result is CompletionResult.Failure -> status.update(ClaudeStatus.Error(result.kind.label()))
            else -> {
                status.blockUntil(null)
                status.update(ClaudeStatus.Ready)
            }
        }
        return result
    }

    private suspend fun pingBackend(config: BackendConfig): CompletionResult =
        engine.complete(
            CompletionContext(
                filePath = "test.kt",
                languageId = "kotlin",
                prefix = "fun add(a: Int, b: Int): Int {\n    return ",
                suffix = "\n}\n",
                openFiles = emptyList(),
                multiline = false,
                maxLines = 1,
            ),
            config,
        )

    override fun dispose() {
        engine.shutdown()
    }

    companion object {
        fun getInstance(): BackendService = service()
    }
}
