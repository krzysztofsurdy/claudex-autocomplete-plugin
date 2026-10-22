package dev.ksurdy.claudeautocomplete.alternatives

import com.intellij.codeInsight.inline.completion.InlineCompletion
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
        val session = claudeSession(editor)
        if (session == null) original.execute(editor, caret, dataContext) else showNewer(session, editor)
    }

    private fun showNewer(session: InlineCompletionSession, editor: Editor) {
        val snapshot = session.capture() ?: return
        if (snapshot.activeVariant.index != 0) return session.usePrevVariant()
        if (snapshot.variantsNumber >= AlternativeRequest.MAX_VARIANTS) return session.usePrevVariant()
        if (snapshot.variants.any { it.state != InlineCompletionVariant.Snapshot.State.COMPUTED || it.isEmpty() }) return
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
        AlternativeRequest.put(editor, AlternativeRequest(snapshot.variants.map { variant -> variant.elements.joinToString("") { it.text } }))
        handler.invokeEvent(ClaudeInlineCompletionProvider.manualCall(editor))
    }
}
