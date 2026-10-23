package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase

object LookupClosedTrigger {
    private val MARK = Key.create<Boolean>("dev.ksurdy.claudeautocomplete.lookupClosed")
    private val LOG = Logger.getInstance(LookupClosedTrigger::class.java)

    fun event(editor: Editor): InlineCompletionEvent.ManualCall =
        ClaudeInlineCompletionProvider.manualCall(editor, UserDataHolderBase().also { it.putUserData(MARK, true) })

    fun isAutomatic(event: InlineCompletionEvent): Boolean = when (event) {
        is InlineCompletionEvent.DocumentChange -> true
        is InlineCompletionEvent.ManualCall -> event.additionalData.getUserData(MARK) == true
        else -> false
    }

    fun onClosed(editor: Editor) {
        val settings = ClaudeAutocompleteSettings.getInstance()
        if (!settings.isActive || settings.state.suggestWhileLookupOpen) return
        val stamp = editor.document.modificationStamp
        ApplicationManager.getApplication()
            .invokeLater({ request(editor, stamp) }, ModalityState.defaultModalityState(), { editor.isDisposed })
    }

    private fun request(editor: Editor, stamp: Long) {
        if (editor.document.modificationStamp != stamp) return
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
        LOG.debug { "Lookup closed: requesting a suggestion" }
        handler.invokeEvent(event(editor))
    }
}

class LookupClosedListener : LookupManagerListener {
    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (oldLookup != null && newLookup == null) LookupClosedTrigger.onClosed(oldLookup.editor)
    }
}
