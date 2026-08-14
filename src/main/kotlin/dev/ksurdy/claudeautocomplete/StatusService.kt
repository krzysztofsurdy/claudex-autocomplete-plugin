package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import java.util.concurrent.CopyOnWriteArrayList

sealed interface ClaudeStatus {
    data object Ready : ClaudeStatus
    data object Thinking : ClaudeStatus
    data class Error(val message: String) : ClaudeStatus
}

@Service(Service.Level.APP)
class StatusService {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var status: ClaudeStatus = ClaudeStatus.Ready
        private set

    fun update(newStatus: ClaudeStatus) {
        if (status == newStatus) return
        status = newStatus
        listeners.forEach { it() }
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

    fun displayText(enabled: Boolean): String = when {
        !enabled -> "Claude: Disabled"
        else -> when (val s = status) {
            ClaudeStatus.Ready -> "Claude: Ready"
            ClaudeStatus.Thinking -> "Claude: Thinking..."
            is ClaudeStatus.Error -> "Claude: Error: ${s.message}"
        }
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
    FailureKind.Other -> "request failed"
}
