package dev.ksurdy.claudeautocomplete

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

class ConsentPrompt : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ClaudeAutocompleteSettings.getInstance().state.consentGiven) return
        if (!askedThisSession.compareAndSet(false, true)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup(Notifier.GROUP_ID)
            .createNotification("Claudex Autocomplete", MESSAGE, NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring("Enable") {
                ClaudeAutocompleteSettings.getInstance().grantConsent()
                StatusService.getInstance().refresh()
            })
            .addAction(Notifier.openSettingsAction("Settings"))
            .addAction(NotificationAction.createSimpleExpiring("Not now") {})
            .notify(project)
    }

    companion object {
        const val MESSAGE =
            "Shows AI completions by running your local Claude Code or Codex CLI. " +
                "Code around the cursor, and optionally open tabs and imported class outlines, " +
                "is sent to Anthropic or OpenAI through that CLI."

        private val askedThisSession = AtomicBoolean(false)
    }
}
