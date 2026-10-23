package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import dev.ksurdy.claudeautocomplete.completion.TriggerRules

object SuggestionMode {
    private val REQUESTED_MULTILINE = Key.create<Boolean>("dev.ksurdy.claudeautocomplete.requestedMultiline")
    private const val WINDOW = 1000
    private val LOG = Logger.getInstance(SuggestionMode::class.java)

    fun dataFor(multiline: Boolean): UserDataHolderBase = UserDataHolderBase().also { it.putUserData(REQUESTED_MULTILINE, multiline) }

    class UpdateManager : InlineCompletionSuggestionUpdateManager.Default() {
        override fun onDocumentChange(
            event: InlineCompletionEvent.DocumentChange,
            snapshot: InlineCompletionVariant.Snapshot,
        ): InlineCompletionSuggestionUpdateManager.UpdateResult {
            if (snapshot.data.getUserData(REQUESTED_MULTILINE) == false && outgrown(event)) {
                LOG.debug { "Suggestion dropped: single-line suggestion does not fit the typed position" }
                return InlineCompletionSuggestionUpdateManager.UpdateResult.Invalidated
            }
            return super.onDocumentChange(event, snapshot)
        }
    }

    private fun outgrown(event: InlineCompletionEvent.DocumentChange): Boolean =
        multilineAt(event.editor, event.typing.range.startOffset + event.typing.typed.length)

    private fun multilineAt(editor: Editor, caret: Int): Boolean {
        val text = editor.document.charsSequence
        val offset = caret.coerceIn(0, text.length)
        val prefix = text.subSequence((offset - WINDOW).coerceAtLeast(0), offset).toString()
        val suffix = text.subSequence(offset, (offset + WINDOW).coerceAtMost(text.length)).toString()
        return TriggerRules.isMultiline(prefix, suffix, ClaudeAutocompleteSettings.getInstance().state.multilineMode.orEmpty())
    }
}
