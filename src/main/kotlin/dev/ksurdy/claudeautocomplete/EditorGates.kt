package dev.ksurdy.claudeautocomplete

import com.intellij.analysis.AnalysisBundle
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import dev.ksurdy.claudeautocomplete.completion.CompletionGates
import dev.ksurdy.claudeautocomplete.completion.EditorSnapshot
import dev.ksurdy.claudeautocomplete.completion.Gate
import dev.ksurdy.claudeautocomplete.completion.RequestStamp
import dev.ksurdy.claudeautocomplete.nextedit.NextEditController

object EditorGates {
    private val LAST_REQUEST = Key.create<LastRequest>("claude.autocomplete.last.request")
    private val SUPPORTED_KINDS = setOf(EditorKind.MAIN_EDITOR, EditorKind.UNTYPED)
    private val TEMPLATE_COMMANDS by lazy {
        listOfNotNull(
            AnalysisBundle.message("insert.code.template.command"),
            ActionManager.getInstance().getAction("ExpandLiveTemplateByTab")?.templatePresentation?.text,
        )
    }

    private class LastRequest(val stamp: RequestStamp, val event: InlineCompletionEvent)

    fun blockedBy(event: InlineCompletionEvent, settings: ClaudeAutocompleteSettings.State): Gate? {
        val editor = editorOf(event) ?: return null
        val snapshot = ReadAction.compute<EditorSnapshot, RuntimeException> { snapshot(editor) }
        val last = editor.getUserData(LAST_REQUEST)?.takeIf { it.event !== event }?.stamp
        val gate = CompletionGates.blockedBy(snapshot, isManual(event), settings.suggestWhileLookupOpen, last)
        if (gate == null) editor.putUserData(LAST_REQUEST, LastRequest(snapshot.stamp, event))
        return gate
    }

    suspend fun blockedAfterDebounce(event: InlineCompletionEvent, settings: ClaudeAutocompleteSettings.State): Gate? {
        val editor = editorOf(event) ?: return null
        val snapshot = readAction { snapshot(editor) }
        return CompletionGates.blockedBy(snapshot, isManual(event), settings.suggestWhileLookupOpen, last = null)
    }

    private fun editorOf(event: InlineCompletionEvent): Editor? = when (event) {
        is InlineCompletionEvent.DocumentChange -> event.editor
        is InlineCompletionEvent.DirectCall -> event.editor
        is InlineCompletionEvent.ManualCall -> event.editor
        else -> null
    }

    private fun isManual(event: InlineCompletionEvent): Boolean = event !is InlineCompletionEvent.DocumentChange

    private fun snapshot(editor: Editor): EditorSnapshot {
        val project = editor.project
        val document = editor.document
        val psiFile = project?.let { PsiDocumentManager.getInstance(it).getCachedPsiFile(document) }
        return EditorSnapshot(
            stamp = RequestStamp(document.modificationStamp, editor.caretModel.offset),
            viewer = editor.isViewer,
            writable = document.isWritable,
            oneLine = editor.isOneLineMode,
            embedded = isEmbedded(editor),
            injectedFragment = editor is EditorWindow || document is DocumentWindow ||
                (project != null && psiFile != null && InjectedLanguageManager.getInstance(project).isInjectedFragment(psiFile)),
            supportedKind = editor.editorKind in SUPPORTED_KINDS,
            undoRedoInProgress = isUndoOrRedo(editor),
            bulkUpdate = document.isInBulkUpdate,
            hasSelection = editor.selectionModel.hasSelection(true),
            caretCount = editor.caretModel.caretCount,
            liveTemplateActive = project != null && TemplateManager.getInstance(project).getActiveTemplate(editor) != null,
            liveTemplateCommand = CommandProcessor.getInstance().currentCommandName?.let { it in TEMPLATE_COMMANDS } == true,
            lookupOpen = LookupManager.getActiveLookup(editor) != null,
            nextEditShown = NextEditController.of(editor)?.state?.isVisible == true,
        )
    }

    private fun isEmbedded(editor: Editor): Boolean {
        if ((editor as? EditorEx)?.isEmbeddedIntoDialogWrapper == true) return true
        val component = editor.contentComponent
        return DialogWrapper.findInstance(component) != null || PopupUtil.getPopupContainerFor(component) != null
    }

    private fun isUndoOrRedo(editor: Editor): Boolean =
        UndoManager.getGlobalInstance().isUndoOrRedoInProgress ||
            editor.project?.let { UndoManager.getInstance(it).isUndoOrRedoInProgress } == true
}
