package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class NextEditEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (editor.project != null && editor.editorKind in SUPPORTED_KINDS) NextEditController.attach(editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) = NextEditController.detach(event.editor)

    private companion object {
        val SUPPORTED_KINDS = setOf(EditorKind.MAIN_EDITOR, EditorKind.UNTYPED)
    }
}
