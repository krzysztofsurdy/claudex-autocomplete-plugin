package dev.ksurdy.claudeautocomplete.backend

import dev.ksurdy.claudeautocomplete.backend.FakeClaudeProcess.Companion.result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OneShotClaudeBackendTest {
    private val locator = ClaudeCliLocator(isExecutable = { true }, shellLookup = { null })

    private fun backend(process: FakeClaudeProcess) = OneShotClaudeBackend(locator) { _, _, persistent ->
        assertEquals(false, persistent)
        process
    }

    private fun answering(output: String, isError: Boolean = false) = FakeClaudeProcess { line ->
        if (line == "<EOF>") {
            emit(result(output, isError))
            finish()
        }
    }

    @Test
    fun `writes raw prompt to stdin and closes it`() = runBlocking {
        val process = answering("x")
        backend(process).complete(testContext(), testConfig())
        assertContains(process.sent[0], "<CURSOR/>")
        assertTrue(process.inputClosed)
    }

    @Test
    fun `returns result text`() = runBlocking {
        assertEquals(CompletionResult.Success("foo"), backend(answering("foo")).complete(testContext(), testConfig()))
    }

    @Test
    fun `blank result is Empty`() = runBlocking {
        assertEquals(CompletionResult.Empty, backend(answering("")).complete(testContext(), testConfig()))
    }

    @Test
    fun `error result maps to invalid model`() = runBlocking {
        val result = backend(answering("There's an issue with the selected model (x)", true)).complete(testContext(), testConfig())
        assertEquals(FailureKind.InvalidModel, assertIs<CompletionResult.Failure>(result).kind)
    }

    @Test
    fun `no result line yields failure`() = runBlocking {
        val process = FakeClaudeProcess { if (it == "<EOF>") finish() }
        assertIs<CompletionResult.Failure>(backend(process).complete(testContext(), testConfig()))
    }

    @Test
    fun `timeout kills process`() = runBlocking {
        val process = FakeClaudeProcess()
        val result = backend(process).complete(testContext(), testConfig(requestTimeoutMs = 100))
        assertEquals(FailureKind.Timeout, assertIs<CompletionResult.Failure>(result).kind)
        assertTrue(process.killed)
    }

    @Test
    fun `cancellation kills process`() = runBlocking {
        val process = FakeClaudeProcess()
        val job = async(Dispatchers.Default) { backend(process).complete(testContext(), testConfig()) }
        while (!process.inputClosed) delay(10)
        job.cancelAndJoin()
        assertTrue(process.killed)
    }

    @Test
    fun `missing binary reports CliNotFound`() = runBlocking {
        val none = OneShotClaudeBackend(ClaudeCliLocator(isExecutable = { false }, shellLookup = { null })) { _, _, _ -> error("no") }
        val result = none.complete(testContext(), testConfig(claudePath = ""))
        assertEquals(FailureKind.CliNotFound, assertIs<CompletionResult.Failure>(result).kind)
    }
}
