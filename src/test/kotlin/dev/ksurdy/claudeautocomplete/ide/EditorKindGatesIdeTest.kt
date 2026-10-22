package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider
import kotlin.time.Duration.Companion.seconds

class EditorKindGatesIdeTest : GatesIdeTestBase() {
    private fun enabledFor(create: (Document) -> Editor): Pair<Boolean, Boolean> {
        var result = false to false
        onEdt {
            val factory = EditorFactory.getInstance()
            val editor = create(factory.createDocument("hello "))
            try {
                val provider = ClaudeInlineCompletionProvider()
                val automatic = provider.isEnabled(InlineCompletionEvent.DocumentChange(TypingEvent.OneSymbol('x', 0), editor))
                val manual = provider.isEnabled(InlineCompletionEvent.DirectCall(editor, editor.caretModel.primaryCaret))
                result = automatic to manual
            } finally {
                factory.releaseEditor(editor)
            }
        }
        return result
    }

    fun testAPlainEditorIsEnabledForBothKindsOfRequest() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        assertEquals(true to true, enabledFor { EditorFactory.getInstance().createEditor(it, project) })
    }

    fun testViewersAreNeverEnabled() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        assertEquals(false to false, enabledFor { EditorFactory.getInstance().createViewer(it, project) })
    }

    fun testOneLineEditorsAreNeverEnabled() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        val oneLine = { document: Document ->
            (EditorFactory.getInstance().createEditor(document, project) as EditorEx).also { it.isOneLineMode = true }
        }
        assertEquals(false to false, enabledFor(oneLine))
    }

    fun testConsoleEditorsAreNeverEnabled() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        assertEquals(false to false, enabledFor { EditorFactory.getInstance().createEditor(it, project, EditorKind.CONSOLE) })
    }

    fun testEditorsEmbeddedIntoADialogAreNeverEnabled() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        val embedded = { document: Document ->
            (EditorFactory.getInstance().createEditor(document, project) as EditorEx).also { it.setEmbeddedIntoDialogWrapper(true) }
        }
        assertEquals(false to false, enabledFor(embedded))
    }
}
