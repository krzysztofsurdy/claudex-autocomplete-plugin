package dev.ksurdy.claudeautocomplete

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class Notifier {
    private val lastShown = ConcurrentHashMap<FailureKind, Long>()

    fun notifyFailure(kind: FailureKind, message: String, now: Long = System.currentTimeMillis()) {
        val content = contentFor(kind, message) ?: return
        if (!shouldNotify(kind, now)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Claude Autocomplete", content, NotificationType.WARNING)
            .notify(null)
    }

    fun shouldNotify(kind: FailureKind, now: Long): Boolean {
        var allowed = false
        lastShown.compute(kind) { _, previous ->
            if (previous == null || now - previous >= THROTTLE_MS) {
                allowed = true
                now
            } else {
                previous
            }
        }
        return allowed
    }

    private fun contentFor(kind: FailureKind, message: String): String? = when (kind) {
        FailureKind.NotLoggedIn -> "Claude CLI is not logged in. Run `claude` in a terminal, then `/login`."
        FailureKind.CliNotFound -> "Claude CLI not found. Install it or set its path in Settings > Tools > Claude Autocomplete."
        FailureKind.InvalidModel -> "Invalid model. Check the model name in Settings > Tools > Claude Autocomplete. $message"
        FailureKind.Timeout, FailureKind.Other -> null
    }

    companion object {
        const val GROUP_ID = "Claude Autocomplete"
        const val THROTTLE_MS = 5 * 60 * 1000L

        fun getInstance(): Notifier = service()
    }
}
