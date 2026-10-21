package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class NextEditTabHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        NextEditController.of(editor)?.canHandleTab() == true || original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val controller = NextEditController.of(editor)
        if (controller != null && controller.canHandleTab()) controller.onTab() else original.execute(editor, caret, dataContext)
    }
}

class NextEditEscapeHandler(private val original: EditorActionHandler) : EditorActionHandler() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
        NextEditController.of(editor)?.canHandleEscape() == true || original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val controller = NextEditController.of(editor)
        if (controller != null && controller.canHandleEscape()) controller.onEscape() else original.execute(editor, caret, dataContext)
    }
}
