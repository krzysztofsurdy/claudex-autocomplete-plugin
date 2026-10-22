package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.nextedit.NextEdit
import dev.ksurdy.claudeautocomplete.nextedit.NextEditController
import dev.ksurdy.claudeautocomplete.nextedit.NextEditService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class NextEditInlineCompletionIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Success("world"))
        service.engine = fake
        NextEditService.getInstance().reset()
        ClaudeAutocompleteSettings.getInstance().state.apply {
            debounceMs = 0
            consentGiven = true
            nextEditSuggestions = true
        }
    }

    override fun tearDown() {
        try {
            BackendService.getInstance().engine = original
            StatusService.getInstance().blockUntil(null)
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private fun onEdt(block: () -> Unit) = com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(block)

    fun testInlineCompletionKeepsTabWhileAGhostSuggestionIsVisible() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(PlainTextFileType.INSTANCE, "first line\nsecond line\nhello <caret>")
        onEdt {
            val controller = NextEditController.of(myFixture.editor) ?: error("no controller")
            controller.state.show(NextEdit(1, 1, 0, 10, "first line", "FIRST"))
            assertTrue(controller.canHandleTab())
        }
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
        onEdt {
            val controller = NextEditController.of(myFixture.editor) ?: error("no controller")
            assertFalse(controller.canHandleTab())
            assertFalse(controller.canHandleEscape())
        }
        insert()
        assertFileContent("first line\nsecond line\nhello world<caret>")
    }

    fun testNoNextEditRequestWhileAGhostSuggestionIsVisible() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.debounceMs = 150
        fake.responder = { context ->
            if (context.nextEdit != null) CompletionResult.Success("<edit start_line=\"1\" end_line=\"1\">FIRST</edit>") else CompletionResult.Success("world")
        }
        init(PlainTextFileType.INSTANCE, "first line\nsecond line\nhello<caret>")
        typeChar(' ')
        delay()
        assertInlineElements { gray("world") }
        delay(600)
        assertTrue(fake.contexts.none { it.nextEdit != null })
        onEdt { assertFalse(NextEditController.of(myFixture.editor)?.state?.isVisible ?: true) }
    }

    private suspend fun realDelay(ms: Long) = withContext(Dispatchers.IO) { kotlinx.coroutines.delay(ms) }

    private suspend fun awaitReal(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !condition()) realDelay(50)
        return condition()
    }

    private fun nextEditRequests() = fake.contexts.count { it.nextEdit != null }

    private fun inlineBusy(): Boolean {
        var busy = false
        onEdt { busy = NextEditController.inlineBusy(myFixture.editor) }
        return busy
    }

    fun testInlineCompletionCountsAsBusyOnceRequestedAndWhileShown() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.responder = { context ->
            if (context.nextEdit != null) CompletionResult.Success("<edit start_line=\"1\" end_line=\"1\">FIRST</edit>") else CompletionResult.Success("world")
        }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fake.gate = gate
        init(PlainTextFileType.INSTANCE, "first line\nsecond line\nhello<caret>")
        assertFalse(inlineBusy())
        typeChar(' ')
        assertTrue(awaitReal { inlineBusy() })
        assertTrue(fake.contexts.none { it.nextEdit != null })
        gate.complete(Unit)
        delay()
        assertInlineElements { gray("world") }
        assertTrue(inlineBusy())
        escape()
        assertTrue(awaitReal { !inlineBusy() })
    }

    fun testNextEditGoesOutOnceTheGhostSuggestionIsGone() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.debounceMs = 100
        fake.responder = { context ->
            if (context.nextEdit != null) CompletionResult.Success("<edit start_line=\"1\" end_line=\"1\">FIRST</edit>") else CompletionResult.Success("world")
        }
        init(PlainTextFileType.INSTANCE, "first line\nsecond line\nhello<caret>")
        typeChar(' ')
        delay()
        assertInlineElements { gray("world") }
        realDelay(1_000)
        assertEquals(0, nextEditRequests())
        escape()
        assertTrue(awaitReal { nextEditRequests() > 0 })
    }
}
