package dev.ksurdy.claudeautocomplete

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

class ConsentPrompt : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!ClaudeAutocompleteSettings.getInstance().state.needsConsentPrompt) return
        if (!askedThisSession.compareAndSet(false, true)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(ClaudexBundle.message("plugin.name"), message(), NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring(ClaudexBundle.message("notification.action.enable")) {
                ClaudeAutocompleteSettings.getInstance().grantConsent()
                StatusService.getInstance().refresh()
            })
            .addAction(Notifier.openSettingsAction(ClaudexBundle.message("notification.action.settings")))
            .addAction(NotificationAction.createSimpleExpiring(ClaudexBundle.message("notification.action.not.now")) { ClaudeAutocompleteSettings.getInstance().declineConsent() })
            .notify(project)
    }

    companion object {
        const val GROUP_ID = "Claudex Autocomplete Consent"

        fun message(): String = ClaudexBundle.message("consent.message")

        private val askedThisSession = AtomicBoolean(false)
    }
}
