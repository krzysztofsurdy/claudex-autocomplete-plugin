package dev.ksurdy.claudeautocomplete.alternatives

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class PrevAlternativeHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val session = claudeSession(editor)
        if (session == null) original.execute(editor, caret ?: editor.caretModel.currentCaret, dataContext) else session.useNextVariant()
    }
}
