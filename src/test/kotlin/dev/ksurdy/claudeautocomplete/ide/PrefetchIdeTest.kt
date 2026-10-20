package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.InlineCompletionLifecycleTestDSL
import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.CompletionRequests
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class PrefetchIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Success("world"))
        service.engine = fake
        CompletionRequests.getInstance().cancel()
        ClaudeAutocompleteSettings.getInstance().state.debounceMs = 0
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = true
    }

    override fun tearDown() {
        try {
            fake.gate?.complete(Unit)
            CompletionRequests.getInstance().cancel()
            BackendService.getInstance().engine = original
            StatusService.getInstance().blockUntil(null)
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

    private suspend fun settled() {
        CompletionRequests.getInstance().prefetch?.join()
    }

    private suspend fun InlineCompletionLifecycleTestDSL.acceptFirstSuggestion(text: String) {
        init(PlainTextFileType.INSTANCE, text)
        callInlineCompletion()
        delay()
        assertInlineRender("world")
    }

    fun testAcceptingAWholeSuggestionPrefetchesTheNextOneAtTheNewCaret() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(CompletionResult.Success("world"), CompletionResult.Success(" next"))
        acceptFirstSuggestion("prefetch1 <caret>")
        insert()
        settled()
        assertEquals(listOf("prefetch1 ", "prefetch1 world"), fake.contexts.map { it.prefix })
        callInlineCompletion()
        delay()
        assertInlineRender(" next")
        assertEquals(2, fake.contexts.size)
    }

    fun testTypingTheStartOfAPrefetchedSuggestionServesTheRestFromTheCache() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(CompletionResult.Success("world"), CompletionResult.Success(" next"))
        acceptFirstSuggestion("prefetch2 <caret>")
        insert()
        settled()
        typeChar(' ')
        delay()
        assertInlineRender("next")
        assertEquals(2, fake.contexts.size)
    }

    fun testAcceptingOnlyAWordDoesNotPrefetch() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.result = CompletionResult.Success("hello world")
        init(PlainTextFileType.INSTANCE, "prefetch3 <caret>")
        callInlineCompletion()
        delay()
        callAction("InsertInlineCompletionWordAction")
        settled()
        assertEquals(1, fake.contexts.size)
    }

    fun testPrefetchCanBeSwitchedOff() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        acceptFirstSuggestion("prefetch4 <caret>")
        ClaudeAutocompleteSettings.getInstance().state.prefetchAfterAccept = false
        insert()
        assertNull(CompletionRequests.getInstance().prefetch)
        assertEquals(1, fake.contexts.size)
    }

    fun testNoPrefetchWithoutConsent() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        acceptFirstSuggestion("prefetch5 <caret>")
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = false
        insert()
        assertNull(CompletionRequests.getInstance().prefetch)
        assertEquals(1, fake.contexts.size)
    }

    fun testNoPrefetchWhenCompletionsAreDisabled() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        acceptFirstSuggestion("prefetch6 <caret>")
        ClaudeAutocompleteSettings.getInstance().state.enabled = false
        insert()
        assertNull(CompletionRequests.getInstance().prefetch)
        assertEquals(1, fake.contexts.size)
    }

    fun testNoPrefetchInExcludedFiles() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        acceptFirstSuggestion("prefetch7 <caret>")
        ClaudeAutocompleteSettings.getInstance().state.excludedFilePatterns = "*.txt"
        insert()
        settled()
        assertEquals(1, fake.contexts.size)
    }

    fun testNoPrefetchWhileTheUsageLimitBlocks() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        acceptFirstSuggestion("prefetch8 <caret>")
        StatusService.getInstance().blockUntil(Instant.now().plusSeconds(3600))
        insert()
        assertNull(CompletionRequests.getInstance().prefetch)
        assertEquals(1, fake.contexts.size)
    }

    fun testInsertingDoesNotWaitForThePrefetch() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        acceptFirstSuggestion("prefetch9 <caret>")
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        insert()
        assertFileContent("prefetch9 world<caret>")
        awaitUntil("prefetch request") { fake.contexts.size == 2 }
        gate.complete(Unit)
        settled()
    }

    fun testAskingForTheSamePositionAdoptsARunningPrefetch() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(CompletionResult.Success("world"), CompletionResult.Success(" next"))
        acceptFirstSuggestion("prefetch10 <caret>")
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        insert()
        awaitUntil("prefetch request") { fake.contexts.size == 2 }
        callInlineCompletion()
        gate.complete(Unit)
        delay()
        assertInlineRender(" next")
        assertEquals(2, fake.contexts.size)
        assertEquals(0, fake.cancellations.get())
    }

    fun testTypingAwayCancelsARunningPrefetch() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        acceptFirstSuggestion("prefetch11 <caret>")
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        fake.partials = listOf(" nex")
        insert()
        awaitUntil("prefetch request") { fake.contexts.size == 2 }
        typeChar('x')
        awaitUntil("cancelled prefetch") { fake.cancellations.get() >= 1 }
        gate.complete(Unit)
    }

    fun testALaterPrefetchReplacesTheRunningOne() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(PlainTextFileType.INSTANCE, "prefetch12 a\nsecond <caret>")
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        val requests = CompletionRequests.getInstance()
        requests.prefetchAfterAccept(myFixture.editor)
        awaitUntil("first prefetch") { fake.contexts.size == 1 }
        ApplicationManager.getApplication().invokeAndWait { myFixture.editor.caretModel.moveToOffset("prefetch12 a".length) }
        requests.prefetchAfterAccept(myFixture.editor)
        awaitUntil("second prefetch") { fake.contexts.size == 2 }
        assertEquals(1, fake.cancellations.get())
        assertEquals("prefetch12 a", fake.contexts.last().prefix)
        gate.complete(Unit)
        settled()
    }

    fun testPrefetchingTheSamePositionTwiceRunsOneRequest() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(PlainTextFileType.INSTANCE, "prefetch13 <caret>")
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        val requests = CompletionRequests.getInstance()
        requests.prefetchAfterAccept(myFixture.editor)
        awaitUntil("prefetch request") { fake.contexts.size == 1 }
        requests.prefetchAfterAccept(myFixture.editor)
        settled()
        assertEquals(1, fake.contexts.size)
        assertEquals(0, fake.cancellations.get())
        gate.complete(Unit)
    }

    fun testARateLimitedPrefetchBlocksFurtherRequests() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(CompletionResult.Success("world"), CompletionResult.Failure(FailureKind.RateLimited, "limit"))
        acceptFirstSuggestion("prefetch14 <caret>")
        insert()
        settled()
        assertTrue(StatusService.getInstance().isBlocked(Instant.now()))
    }

    fun testATimedOutPrefetchStaysSilent() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(CompletionResult.Success("world"), CompletionResult.Failure(FailureKind.Timeout, "slow"))
        acceptFirstSuggestion("prefetch15 <caret>")
        insert()
        settled()
        assertFalse(StatusService.getInstance().status is ClaudeStatus.Error)
        assertFalse(StatusService.getInstance().isBlocked(Instant.now()))
    }

    fun testAFailedPrefetchLeavesNothingInTheCache() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(CompletionResult.Success("world"), CompletionResult.Empty, CompletionResult.Success(" later"))
        acceptFirstSuggestion("prefetch16 <caret>")
        insert()
        settled()
        callInlineCompletion()
        delay()
        assertInlineRender(" later")
        assertEquals(3, fake.contexts.size)
    }
}
