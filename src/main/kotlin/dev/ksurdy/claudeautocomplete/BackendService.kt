package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.ClaudeCliBackend
import dev.ksurdy.claudeautocomplete.backend.ClaudeConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionBackend
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import java.time.Instant

@Service(Service.Level.APP)
class BackendService : Disposable {
    @Volatile
    var backend: CompletionBackend = ClaudeCliBackend().also { cli ->
        cli.addUsageListener { StatusService.getInstance().updateUsage(it) }
    }

    suspend fun ping(config: ClaudeConfig): CompletionResult {
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

    private suspend fun pingBackend(config: ClaudeConfig): CompletionResult =
        backend.complete(
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
        backend.shutdown()
    }

    companion object {
        fun getInstance(): BackendService = service()
    }
}
