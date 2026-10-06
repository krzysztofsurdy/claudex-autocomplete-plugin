package dev.ksurdy.claudeautocomplete

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.text.StringUtil
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
            .createNotification(ClaudexBundle.message("plugin.name"), content, NotificationType.WARNING)
            .addAction(openSettingsAction(ClaudexBundle.message("notification.action.open.settings")))
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

        fun contentFor(provider: String, kind: FailureKind, message: String): String? {
            val codex = provider == CODEX
            val escaped = StringUtil.escapeXmlEntities(message)
            return when (kind) {
                FailureKind.NotLoggedIn -> ClaudexBundle.message(if (codex) "failure.codex.not.logged.in" else "failure.claude.not.logged.in")
                FailureKind.CliNotFound -> ClaudexBundle.message(if (codex) "failure.codex.cli.not.found" else "failure.claude.cli.not.found")
                FailureKind.InvalidModel -> ClaudexBundle.message("failure.invalid.model", provider, escaped)
                FailureKind.RateLimited ->
                    if (message.isBlank()) ClaudexBundle.message("failure.rate.limited", provider)
                    else ClaudexBundle.message("failure.rate.limited.reset", provider, escaped)
                FailureKind.Timeout, FailureKind.Other -> null
            }
        }
    }
}
