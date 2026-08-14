package dev.ksurdy.claudeautocomplete.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.StatusService

class ToggleAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean = ClaudeAutocompleteSettings.getInstance().state.enabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        ClaudeAutocompleteSettings.getInstance().state.enabled = state
        StatusService.getInstance().refresh()
    }
}
