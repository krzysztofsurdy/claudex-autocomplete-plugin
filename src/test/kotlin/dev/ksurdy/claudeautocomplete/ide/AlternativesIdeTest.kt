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
import kotlin.time.Duration.Companion.seconds

class AlternativesIdeTest : BasePlatformTestCase() {
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
            BackendService.getInstance().engine = original
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testADuplicateAlternativeIsRetriedAndTheDistinctReplyIsShown() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(
            CompletionResult.Success("first"),
            CompletionResult.Success("first"),
            CompletionResult.Success("second"),
        )
        init(PlainTextFileType.INSTANCE, "dup1 <caret>")
        callInlineCompletion()
        delay()
        assertInlineRender("first")
        callAction(NEXT_SUGGESTION)
        delay()
        assertInlineRender("second")
        assertEquals(3, fake.contexts.size)
        assertEquals(listOf(false, false, true), fake.contexts.map { it.distinctRetry })
        assertEquals(listOf("first"), fake.contexts.last().alternativeTo)
    }

    fun testADuplicateThatDiffersOnlyInWhitespaceAndPunctuationIsRetried() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(
            CompletionResult.Success("foo(1, 2);"),
            CompletionResult.Success("foo(1,   2)"),
            CompletionResult.Success("bar()"),
        )
        init(PlainTextFileType.INSTANCE, "dup2 <caret>")
        callInlineCompletion()
        delay()
        callAction(NEXT_SUGGESTION)
        delay()
        assertInlineRender("bar()")
        assertEquals(3, fake.contexts.size)
    }

    fun testTwoDuplicatesShowNoNewVariantAndKeepTheExistingOne() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(
            CompletionResult.Success("first"),
            CompletionResult.Success("first"),
            CompletionResult.Success("first;"),
        )
        init(PlainTextFileType.INSTANCE, "dup3 <caret>")
        callInlineCompletion()
        delay()
        assertInlineRender("first")
        callAction(NEXT_SUGGESTION)
        delay()
        assertInlineRender("first")
        assertEquals(3, fake.contexts.size)
    }

    fun testADistinctAlternativeIsNotRetried() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.queued += listOf(CompletionResult.Success("first"), CompletionResult.Success("second"))
        init(PlainTextFileType.INSTANCE, "dup4 <caret>")
        callInlineCompletion()
        delay()
        callAction(NEXT_SUGGESTION)
        delay()
        assertInlineRender("second")
        assertEquals(2, fake.contexts.size)
        assertTrue(fake.contexts.none { it.distinctRetry })
    }

    private companion object {
        const val NEXT_SUGGESTION = "NextInlineCompletionSuggestionAction"
    }
}
