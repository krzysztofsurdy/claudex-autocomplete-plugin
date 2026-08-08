package dev.ksurdy.claudeautocomplete.backend

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ClaudeCliBackendTest {
    private class Recording(private val results: ArrayDeque<CompletionResult>) : CompletionBackend {
        var calls = 0
        var shutdowns = 0
        override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
            calls++
            return results.removeFirstOrNull() ?: CompletionResult.Success("default")
        }

        override fun shutdown() {
            shutdowns++
        }
    }

    private fun recording(vararg results: CompletionResult) = Recording(ArrayDeque(results.toList()))
    private val other = CompletionResult.Failure(FailureKind.Other, "boom")

    @Test
    fun `persistent mode uses persistent backend`() = runBlocking {
        val persistent = recording()
        val oneShot = recording()
        ClaudeCliBackend(persistent, oneShot).complete(testContext(), testConfig(persistentProcess = true))
        assertEquals(1, persistent.calls)
        assertEquals(0, oneShot.calls)
    }

    @Test
    fun `one shot mode uses one shot backend`() = runBlocking {
        val persistent = recording()
        val oneShot = recording()
        ClaudeCliBackend(persistent, oneShot).complete(testContext(), testConfig(persistentProcess = false))
        assertEquals(0, persistent.calls)
        assertEquals(1, oneShot.calls)
    }

    @Test
    fun `falls back to one shot after two consecutive persistent failures`() = runBlocking {
        val persistent = recording(other, other)
        val oneShot = recording()
        val backend = ClaudeCliBackend(persistent, oneShot)
        backend.complete(testContext(), testConfig())
        backend.complete(testContext(), testConfig())
        assertEquals(CompletionResult.Success("default"), backend.complete(testContext(), testConfig()))
        assertEquals(2, persistent.calls)
        assertEquals(1, oneShot.calls)
    }

    @Test
    fun `success resets the failure counter`() = runBlocking {
        val persistent = recording(other, CompletionResult.Success("a"), other)
        val oneShot = recording()
        val backend = ClaudeCliBackend(persistent, oneShot)
        repeat(4) { backend.complete(testContext(), testConfig()) }
        assertEquals(4, persistent.calls)
        assertEquals(0, oneShot.calls)
    }

    @Test
    fun `config change re-enables persistent mode`() = runBlocking {
        val persistent = recording(other, other)
        val oneShot = recording()
        val backend = ClaudeCliBackend(persistent, oneShot)
        repeat(2) { backend.complete(testContext(), testConfig()) }
        backend.complete(testContext(), testConfig(model = "sonnet"))
        assertEquals(3, persistent.calls)
    }

    @Test
    fun `not logged in does not count as persistent failure`() = runBlocking {
        val notLoggedIn = CompletionResult.Failure(FailureKind.NotLoggedIn, "x")
        val persistent = recording(notLoggedIn, notLoggedIn, notLoggedIn)
        val oneShot = recording()
        val backend = ClaudeCliBackend(persistent, oneShot)
        repeat(3) { backend.complete(testContext(), testConfig()) }
        assertEquals(3, persistent.calls)
        assertEquals(0, oneShot.calls)
    }

    @Test
    fun `shutdown stops both`() {
        val persistent = recording()
        val oneShot = recording()
        ClaudeCliBackend(persistent, oneShot).shutdown()
        assertEquals(1, persistent.shutdowns)
        assertEquals(1, oneShot.shutdowns)
    }
}
