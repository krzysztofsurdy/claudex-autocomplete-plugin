package dev.ksurdy.claudeautocomplete

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class Notifier {
    private val lastShown = ConcurrentHashMap<Pair<String, FailureKind>, Long>()

    fun notifyFailure(kind: FailureKind, message: String, provider: String = CLAUDE, now: Long = System.currentTimeMillis()) {
        val content = contentFor(provider, kind, message) ?: return
        if (!shouldNotify(kind, now, provider)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification("Claudex Autocomplete", content, NotificationType.WARNING)
            .addAction(openSettingsAction("Open Settings"))
            .notify(null)
    }

    fun shouldNotify(kind: FailureKind, now: Long, provider: String = CLAUDE): Boolean {
        var allowed = false
        lastShown.compute(provider to kind) { _, previous ->
            if (previous == null || now - previous >= THROTTLE_MS) {
                allowed = true
                now
            } else {
                previous
            }
        }
        return allowed
    }

    companion object {
        const val GROUP_ID = "Claude Autocomplete"
        const val CLAUDE = "Claude"
        const val CODEX = "Codex"
        const val THROTTLE_MS = 5 * 60 * 1000L

        fun getInstance(): Notifier = service()

        fun openSettingsAction(text: String): NotificationAction = NotificationAction.createSimpleExpiring(text) {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, ClaudeAutocompleteConfigurable::class.java)
        }

        fun contentFor(provider: String, kind: FailureKind, message: String): String? = when (kind) {
            FailureKind.NotLoggedIn -> if (provider == CODEX) {
                "Codex CLI is not logged in. Run <code>codex login</code> in a terminal."
            } else {
                "Claude CLI is not logged in. Run <code>claude</code> in a terminal, then <code>/login</code>."
            }
            FailureKind.CliNotFound -> if (provider == CODEX) {
                "Codex CLI not found. Install it (<code>npm i -g @openai/codex</code> or <code>brew install codex</code>) or set its path in Settings | Tools | Claudex Autocomplete."
            } else {
                "Claude CLI not found. Install it or set its path in Settings | Tools | Claudex Autocomplete."
            }
            FailureKind.InvalidModel -> "Invalid $provider model. Check the model name in Settings | Tools | Claudex Autocomplete. $message"
            FailureKind.RateLimited -> "$provider usage limit reached${if (message.isBlank()) "" else ", $message"}."
            FailureKind.Timeout, FailureKind.Other -> null
        }
    }
}
