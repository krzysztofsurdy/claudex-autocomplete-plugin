package dev.ksurdy.claudeautocomplete.alternatives

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider

class NextAlternativeHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val caret = caret ?: editor.caretModel.currentCaret
        if (!requestAlternative(editor, caret, dataContext)) original.execute(editor, caret, dataContext)
    }

    private fun requestAlternative(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
        val session = InlineCompletionSession.getOrNull(editor) ?: return false
        if (!session.isActive() || session.provider !is ClaudeInlineCompletionProvider) return false
        val snapshot = session.capture() ?: return false
        val active = snapshot.activeVariant
        if (snapshot.variantsNumber >= AlternativeRequest.MAX_VARIANTS || active.index != snapshot.variantsNumber - 1) return false
        if (snapshot.variants.any { it.state != InlineCompletionVariant.Snapshot.State.COMPUTED || it.isEmpty() }) return false
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return false
        AlternativeRequest.put(editor, AlternativeRequest(snapshot.variants.map { variant -> variant.elements.joinToString("") { it.text } }))
        handler.invoke(InlineCompletionEvent.DirectCall(editor, caret, dataContext ?: DataContext.EMPTY_CONTEXT))
        return true
    }
}
