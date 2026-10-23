package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
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

    fun outgrown(event: InlineCompletionEvent.DocumentChange): Boolean {
        val shown = InlineCompletionSession.getOrNull(event.editor)?.capture()?.activeVariant?.data
        if (shown?.getUserData(REQUESTED_MULTILINE) != false) return false
        val caret = event.typing.range.startOffset + event.typing.typed.length
        val outgrown = multilineAt(event.editor, caret)
        if (outgrown) LOG.debug { "Suggestion restarted: single-line suggestion does not fit the typed position" }
        return outgrown
    }

    private fun multilineAt(editor: Editor, caret: Int): Boolean {
        val text = editor.document.charsSequence
        val offset = caret.coerceIn(0, text.length)
        val prefix = text.subSequence((offset - WINDOW).coerceAtLeast(0), offset).toString()
        val suffix = text.subSequence(offset, (offset + WINDOW).coerceAtMost(text.length)).toString()
        return TriggerRules.isMultiline(prefix, suffix, ClaudeAutocompleteSettings.getInstance().state.multilineMode.orEmpty())
    }
}
