package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.CompletionRequests
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.backend.CompletionResult

abstract class GatesIdeTestBase : BasePlatformTestCase() {
    protected lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Success("world"))
        service.engine = fake
        ClaudeAutocompleteSettings.getInstance().state.debounceMs = 0
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = true
    }

    override fun tearDown() {
        try {
            CompletionRequests.getInstance().cancel()
            BackendService.getInstance().engine = original
            StatusService.getInstance().blockUntil(null)
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    protected fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeAndWait(block)

    protected fun typingEvent(char: Char = 'x'): InlineCompletionEvent.DocumentChange {
        val editor = myFixture.editor
        return InlineCompletionEvent.DocumentChange(TypingEvent.OneSymbol(char, editor.caretModel.offset), editor)
    }

    protected fun handler() = InlineCompletion.getHandlerOrNull(myFixture.editor) ?: error("no inline completion handler")

    protected fun insertAtCaret() = WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.document.insertString(myFixture.editor.caretModel.offset, "x")
    }

    protected fun fireAutomatic() = onEdt {
        insertAtCaret()
        handler().invokeEvent(typingEvent())
    }
}
