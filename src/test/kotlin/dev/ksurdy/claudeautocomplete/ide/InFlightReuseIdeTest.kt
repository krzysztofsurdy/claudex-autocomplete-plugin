package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.InlineCompletionLifecycleTestDSL
import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.seconds

class InFlightReuseIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
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
            fake.gate?.complete(Unit)
            BackendService.getInstance().engine = original
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private suspend fun InlineCompletionLifecycleTestDSL.awaitUntil(description: String, condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(50)
        }
        assertTrue(description, condition())
    }

    fun testTypingTheStartOfTheStreamedTextKeepsTheRequestAndTrimsTheResult() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        fake.partials = listOf("wor")
        init(PlainTextFileType.INSTANCE, "reuse1 <caret>")
        callInlineCompletion()
        awaitUntil("first request") { fake.contexts.size == 1 }
        typeChar('w')
        gate.complete(Unit)
        delay()
        assertInlineRender("orld")
        assertEquals(1, fake.contexts.size)
        assertEquals(0, fake.cancellations.get())
    }

    fun testTypingBeforeAnythingStreamedKeepsTheRequest() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        init(PlainTextFileType.INSTANCE, "reuse2 <caret>")
        callInlineCompletion()
        awaitUntil("first request") { fake.contexts.size == 1 }
        typeChar('w')
        gate.complete(Unit)
        delay()
        assertInlineRender("orld")
        assertEquals(1, fake.contexts.size)
        assertEquals(0, fake.cancellations.get())
    }

    fun testSeveralMatchingKeystrokesStillShareOneRequest() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        fake.partials = listOf("wor")
        init(PlainTextFileType.INSTANCE, "reuse3 <caret>")
        callInlineCompletion()
        awaitUntil("first request") { fake.contexts.size == 1 }
        typeChar('w')
        typeChar('o')
        gate.complete(Unit)
        delay()
        assertInlineRender("rld")
        assertEquals(1, fake.contexts.size)
        assertEquals(0, fake.cancellations.get())
    }

    fun testTypingThatDivergesFromTheStreamedTextCancelsTheRequest() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        fake.partials = listOf("wor")
        init(PlainTextFileType.INSTANCE, "reuse4 <caret>")
        callInlineCompletion()
        awaitUntil("first request") { fake.contexts.size == 1 }
        typeChar('x')
        awaitUntil("second request") { fake.contexts.size == 2 }
        assertEquals(1, fake.cancellations.get())
        assertEquals("reuse4 x", fake.contexts.last().prefix)
        gate.complete(Unit)
        delay()
    }

    fun testAResultThatDoesNotFitTheTypedTextIsRequestedAgain() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        fake.queued += listOf(CompletionResult.Success("xyz"), CompletionResult.Success("orld"))
        init(PlainTextFileType.INSTANCE, "reuse5 <caret>")
        callInlineCompletion()
        awaitUntil("first request") { fake.contexts.size == 1 }
        typeChar('w')
        gate.complete(Unit)
        delay()
        assertInlineRender("orld")
        assertEquals(listOf("reuse5 ", "reuse5 w"), fake.contexts.map { it.prefix })
    }

    fun testTypingThatEndsInAnUntriggeredPositionCancelsTheRequest() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        init(PlainTextFileType.INSTANCE, "reuse6 <caret>bar")
        callInlineCompletion()
        awaitUntil("first request") { fake.contexts.size == 1 }
        typeChar('o')
        awaitUntil("cancelled request") { fake.cancellations.get() == 1 }
        assertEquals(1, fake.contexts.size)
        gate.complete(Unit)
    }

    fun testAKeptRequestStillFillsTheCacheForTheOriginalPosition() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        fake.result = CompletionResult.Success("abcdef")
        init(PlainTextFileType.INSTANCE, "reuse7 <caret>")
        callInlineCompletion()
        awaitUntil("first request") { fake.contexts.size == 1 }
        typeChar('a')
        gate.complete(Unit)
        delay()
        assertInlineRender("bcdef")
        escape()
        typeChar('b')
        delay()
        assertInlineRender("cdef")
        assertEquals(1, fake.contexts.size)
    }
}
