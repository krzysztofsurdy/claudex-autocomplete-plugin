package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.util.DocumentUtil
import dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider
import dev.ksurdy.claudeautocomplete.nextedit.NextEdit
import dev.ksurdy.claudeautocomplete.nextedit.NextEditController
import kotlin.time.Duration.Companion.seconds

class EditorStateGatesIdeTest : GatesIdeTestBase() {
    private fun startTemplate() = onEdt {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        val template = TemplateImpl("", "one \$X\$ two", "")
        template.addVariable("X", "", "", true)
        WriteCommandAction.runWriteCommandAction(project) {
            TemplateManager.getInstance(project).startTemplate(myFixture.editor, template)
        }
    }

    private fun selectFirstWord() = onEdt { myFixture.editor.selectionModel.setSelection(0, 5) }

    private fun addSecondCaret() = onEdt { myFixture.editor.caretModel.addCaret(VisualPosition(0, 1)) }

    private fun showNextEdit() = onEdt {
        val controller = NextEditController.of(myFixture.editor) ?: error("no controller")
        controller.state.show(NextEdit(1, 1, 0, 5, "first", "FIRST"))
    }

    fun testAutomaticRequestWithoutAnyGateGoesThrough() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate00 <caret>")
        fireAutomatic()
        delay()
        assertEquals(1, fake.contexts.size)
        assertInlineElements { gray("world") }
    }

    fun testSelectionBlocksAutomaticRequests() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate01 <caret>")
        selectFirstWord()
        fireAutomatic()
        delay()
        assertEquals(0, fake.contexts.size)
        assertInlineHidden()
    }

    fun testSelectionStillAllowsManualTrigger() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate02 <caret>")
        selectFirstWord()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testSelectionStillAllowsDirectCall() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate03 <caret>")
        selectFirstWord()
        callInlineCompletion()
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testSeveralCaretsBlockAutomaticRequests() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate04 <caret>")
        addSecondCaret()
        onEdt { assertEquals(2, myFixture.editor.caretModel.caretCount) }
        fireAutomatic()
        delay()
        assertEquals(0, fake.contexts.size)
    }

    fun testSeveralCaretsAlsoBlockManualTriggers() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate05 <caret>")
        addSecondCaret()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(0, fake.contexts.size)
    }

    private fun fireWhileUndoing(undoable: Boolean) {
        onEdt {
            val editor = myFixture.editor
            val project = project
            val handler = handler()
            val disposable = Disposer.newDisposable()
            try {
                WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "a") }
                editor.document.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        if (UndoManager.getInstance(project).isUndoInProgress == undoable) handler.invokeEvent(typingEvent())
                    }
                }, disposable)
                myFixture.performEditorAction(IdeActions.ACTION_UNDO)
            } finally {
                Disposer.dispose(disposable)
            }
        }
    }

    fun testUndoInProgressBlocksAutomaticRequests() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate06 <caret>")
        fireWhileUndoing(undoable = true)
        delay()
        assertEquals(0, fake.contexts.size)
    }

    fun testTheSameFlowOutsideUndoRequestsNormally() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate07 <caret>")
        onEdt {
            insertAtCaret()
            handler().invokeEvent(typingEvent())
        }
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testRedoInProgressBlocksAutomaticRequests() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate08 <caret>")
        onEdt {
            val editor = myFixture.editor
            WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, "a") }
            myFixture.performEditorAction(IdeActions.ACTION_UNDO)
            val handler = handler()
            val disposable = Disposer.newDisposable()
            try {
                editor.document.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        if (UndoManager.getInstance(project).isRedoInProgress) handler.invokeEvent(typingEvent())
                    }
                }, disposable)
                myFixture.performEditorAction(IdeActions.ACTION_REDO)
            } finally {
                Disposer.dispose(disposable)
            }
        }
        delay()
        assertEquals(0, fake.contexts.size)
    }

    fun testBulkUpdateDisablesTheProviderForAutomaticEvents() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate09 <caret>")
        val provider = ClaudeInlineCompletionProvider()
        var inBulk = true
        var outside = false
        onEdt {
            val editor = myFixture.editor
            WriteCommandAction.runWriteCommandAction(project) {
                DocumentUtil.executeInBulk(editor.document) {
                    editor.document.insertString(editor.caretModel.offset, "x")
                    inBulk = provider.isEnabled(typingEvent())
                }
            }
            insertAtCaret()
            outside = provider.isEnabled(typingEvent())
        }
        assertFalse(inBulk)
        assertTrue(outside)
    }

    fun testActiveLiveTemplateBlocksAutomaticRequests() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate10 <caret>")
        startTemplate()
        onEdt { assertNotNull(TemplateManager.getInstance(project).getActiveTemplate(myFixture.editor)) }
        fireAutomatic()
        delay()
        assertEquals(0, fake.contexts.size)
    }

    fun testLiveTemplateExpansionCommandBlocksAutomaticRequests() = assertTemplateCommandBlocks("ExpandLiveTemplateByTab", "gate11")

    fun testCustomShortcutTemplateCommandBlocksAutomaticRequests() = assertTemplateCommandBlocks("ExpandLiveTemplateCustom", "gate11a")

    fun testInsertTemplateCommandBlocksAutomaticRequests() = assertTemplateCommandBlocks("InsertLiveTemplate", "gate11b")

    fun testSurroundTemplateCommandBlocksAutomaticRequests() = assertTemplateCommandBlocks("SurroundWithLiveTemplate", "gate11c")

    fun testUnrelatedCommandDoesNotBlockAutomaticRequests() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate11d <caret>")
        onEdt {
            CommandProcessor.getInstance().executeCommand(
                project,
                {
                    insertAtCaret()
                    handler().invokeEvent(typingEvent())
                },
                "Unrelated command",
                null,
            )
        }
        delay()
        assertEquals(1, fake.contexts.size)
    }

    private fun assertTemplateCommandBlocks(actionId: String, marker: String) = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "$marker <caret>")
        val commandName = ActionManager.getInstance().getAction(actionId).templatePresentation.text
        onEdt {
            CommandProcessor.getInstance().executeCommand(
                project,
                {
                    insertAtCaret()
                    handler().invokeEvent(typingEvent())
                },
                commandName,
                null,
            )
        }
        delay()
        assertEquals(0, fake.contexts.size)
    }

    fun testManualTriggerStillWorksWithALiveTemplateActive() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate12 <caret>")
        startTemplate()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testShownNextEditBlocksAutomaticRequests() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "first line\ngate13 <caret>")
        onEdt { insertAtCaret() }
        showNextEdit()
        onEdt { handler().invokeEvent(typingEvent()) }
        delay()
        assertEquals(0, fake.contexts.size)
    }

    fun testShownNextEditStillAllowsManualTrigger() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "first line\ngate14 <caret>")
        showNextEdit()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(1, fake.contexts.size)
    }
}
