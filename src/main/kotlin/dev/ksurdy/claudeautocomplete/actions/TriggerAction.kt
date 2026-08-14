package dev.ksurdy.claudeautocomplete.actions

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.UserDataHolderBase
import dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider

class TriggerAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && !editor.isViewer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
        handler.invokeEvent(
            InlineCompletionEvent.ManualCall(
                editor,
                InlineCompletionProviderID(ClaudeInlineCompletionProvider.ID),
                UserDataHolderBase(),
            ),
        )
    }
}
