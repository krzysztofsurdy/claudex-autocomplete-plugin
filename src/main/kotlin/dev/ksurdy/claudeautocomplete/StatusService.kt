package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import dev.ksurdy.claudeautocomplete.backend.UsageLimits
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

sealed interface ClaudeStatus {
    data object Ready : ClaudeStatus
    data class Waiting(val since: Long) : ClaudeStatus
    data class Thinking(val since: Long) : ClaudeStatus
    data class Done(val latencyMs: Long, val at: Long) : ClaudeStatus
    data class Error(val message: String) : ClaudeStatus
    data class LimitReached(val resetsAt: Instant?) : ClaudeStatus
}

@Service(Service.Level.APP)
class StatusService {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var status: ClaudeStatus = ClaudeStatus.Ready
        private set

    @Volatile
    var usage: UsageLimits? = null
        private set

    @Volatile
    var lastLatencyMs: Long? = null
        private set

    @Volatile
    var lastModel: String? = null
        private set

    @Volatile
    var blockedUntil: Instant? = null
        private set

    fun update(newStatus: ClaudeStatus) {
        if (newStatus is ClaudeStatus.Done) lastLatencyMs = newStatus.latencyMs
        if (status == newStatus) return
        status = newStatus
        refresh()
    }

    fun updateUsage(limits: UsageLimits) {
        usage = limits
        refresh()
    }

    fun recordModel(model: String) {
        lastModel = model
    }

    fun blockUntil(instant: Instant?) {
        blockedUntil = instant
    }

    fun registerRateLimit(now: Instant): Instant {
        val resetsAt = StatusFormatter.limitResetsAt(usage, now)
        blockedUntil = resetsAt
        return resetsAt
    }

    fun isBlocked(now: Instant): Boolean {
        val until = blockedUntil ?: return false
        if (now.isBefore(until)) return true
        blockedUntil = null
        return false
    }

    fun refresh() {
        listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    companion object {
        fun getInstance(): StatusService = service()
    }
}

fun FailureKind.label(): String = when (this) {
    FailureKind.NotLoggedIn -> "not logged in"
    FailureKind.CliNotFound -> "CLI not found"
    FailureKind.InvalidModel -> "invalid model"
    FailureKind.Timeout -> "timeout"
    FailureKind.RateLimited -> "limit reached"
    FailureKind.Other -> "request failed"
}
