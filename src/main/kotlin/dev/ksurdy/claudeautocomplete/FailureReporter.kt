package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.diagnostic.Logger
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import java.time.Instant
import java.time.ZoneId

object FailureReporter {
    private const val MAX_LOGGED_MESSAGE = 300
    private val LOG = Logger.getInstance(FailureReporter::class.java)

    fun report(failure: CompletionResult.Failure, provider: ProviderKind): ClaudeStatus {
        LOG.warn("Completion failed: provider=$provider kind=${failure.kind} message=${failure.message.take(MAX_LOGGED_MESSAGE)}")
        if (failure.kind != FailureKind.RateLimited) {
            Notifier.getInstance().notifyFailure(failure.kind, failure.message, provider.displayName())
            return ClaudeStatus.Error(failure.kind.label())
        }
        val resetsAt = StatusService.getInstance().registerRateLimit(Instant.now())
        val clock = StatusFormatter.clock(resetsAt, ZoneId.systemDefault())
        Notifier.getInstance().notifyFailure(failure.kind, "resets at $clock", provider.displayName())
        return ClaudeStatus.LimitReached(resetsAt)
    }
}
