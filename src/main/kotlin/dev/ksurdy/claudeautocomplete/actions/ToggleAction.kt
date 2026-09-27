package dev.ksurdy.claudeautocomplete.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.StatusService

class ToggleAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean = ClaudeAutocompleteSettings.getInstance().isActive

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val settings = ClaudeAutocompleteSettings.getInstance()
        if (state) settings.grantConsent() else settings.state.enabled = false
        StatusService.getInstance().refresh()
    }
}
