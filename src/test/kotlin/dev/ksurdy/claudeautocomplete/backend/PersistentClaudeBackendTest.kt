package dev.ksurdy.claudeautocomplete.backend

import dev.ksurdy.claudeautocomplete.backend.FakeClaudeProcess.Companion.delta
import dev.ksurdy.claudeautocomplete.backend.FakeClaudeProcess.Companion.result
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersistentClaudeBackendTest {
    private val locator = ClaudeCliLocator(isExecutable = { true }, shellLookup = { null })

    private fun isClear(line: String) = line.contains("\"/clear\"")
    private fun isInterrupt(line: String) = line.contains("\"interrupt\"")

    private fun echoProcess(reply: String = "42"): FakeClaudeProcess = FakeClaudeProcess { line ->
        when {
            isClear(line) -> emit(result(""))
            isInterrupt(line) -> emit(result("interrupted"))
            else -> {
                emit(delta(reply.take(1)))
                emit(delta(reply.drop(1)))
                emit(result(reply))
            }
        }
    }

    private fun backend(vararg processes: FakeClaudeProcess, started: MutableList<ClaudeConfig> = mutableListOf()): PersistentClaudeBackend {
        val queue = ArrayDeque(processes.toList())
        return PersistentClaudeBackend(locator) { _, config, persistent ->
            assertTrue(persistent)
            started += config
            queue.removeFirst()
        }
    }

    @Test
    fun `first request sends prompt only and returns streamed text`() = runBlocking {
        val process = echoProcess("42")
        val result = backend(process).complete(testContext(), testConfig())
        assertEquals(CompletionResult.Success("42"), result)
        assertEquals(1, process.sent.size)
        assertContains(process.sent[0], "\"type\":\"user\"")
        assertContains(process.sent[0], "<CURSOR/>")
    }

    @Test
    fun `later requests clear context first`() = runBlocking {
        val process = echoProcess()
        val backend = backend(process)
        backend.complete(testContext(), testConfig())
        backend.complete(testContext(), testConfig())
        assertEquals(3, process.sent.size)
        assertTrue(isClear(process.sent[1]))
        assertContains(process.sent[2], "<CURSOR/>")
    }

    @Test
    fun `reuses the process across requests`() = runBlocking {
        val started = mutableListOf<ClaudeConfig>()
        val backend = backend(echoProcess(), started = started)
        backend.complete(testContext(), testConfig())
        backend.complete(testContext(), testConfig(requestTimeoutMs = 1234))
        assertEquals(1, started.size)
    }

    @Test
    fun `restarts process when config changes`() = runBlocking {
        val first = echoProcess()
        val second = echoProcess()
        val backend = backend(first, second)
        backend.complete(testContext(), testConfig(model = "haiku"))
        backend.complete(testContext(), testConfig(model = "sonnet"))
        assertTrue(first.killed)
        assertEquals(1, second.sent.size)
    }

    @Test
    fun `empty output is Empty`() = runBlocking {
        val process = FakeClaudeProcess { emit(result("")) }
        assertEquals(CompletionResult.Empty, backend(process).complete(testContext(), testConfig()))
    }

    @Test
    fun `error result maps failure kind`() = runBlocking {
        val process = FakeClaudeProcess { emit(result("Not logged in - run /login", isError = true)) }
        val result = backend(process).complete(testContext(), testConfig())
        assertEquals(FailureKind.NotLoggedIn, assertIs<CompletionResult.Failure>(result).kind)
    }

    @Test
    fun `process death yields failure and respawns next time`() = runBlocking {
        val dying = FakeClaudeProcess { finish() }
        val healthy = echoProcess("ok")
        val backend = backend(dying, healthy)
        assertIs<CompletionResult.Failure>(backend.complete(testContext(), testConfig()))
        assertEquals(CompletionResult.Success("ok"), backend.complete(testContext(), testConfig()))
    }

    @Test
    fun `timeout kills process and reports Timeout`() = runBlocking {
        val silent = FakeClaudeProcess()
        val result = backend(silent).complete(testContext(), testConfig(requestTimeoutMs = 100))
        assertEquals(FailureKind.Timeout, assertIs<CompletionResult.Failure>(result).kind)
        assertTrue(silent.killed)
    }

    @Test
    fun `cancellation sends interrupt and keeps process alive`() = runBlocking {
        val process = FakeClaudeProcess { line -> if (isInterrupt(line)) emit(result("interrupted")) }
        val backend = backend(process)
        val job = async(Dispatchers.Default, start = CoroutineStart.DEFAULT) { backend.complete(testContext(), testConfig()) }
        while (process.sent.isEmpty()) delay(10)
        job.cancelAndJoin()
        assertTrue(process.sent.any(::isInterrupt))
        assertFalse(process.killed)
    }

    @Test
    fun `cancelled request is followed by a clean one`() = runBlocking {
        var hang = true
        val process = FakeClaudeProcess { line ->
            when {
                isInterrupt(line) -> emit(result("interrupted"))
                isClear(line) -> emit(result(""))
                !hang -> emit(result("fine"))
            }
        }
        val backend = backend(process)
        val job = async(Dispatchers.Default) { backend.complete(testContext(), testConfig()) }
        while (process.sent.isEmpty()) delay(10)
        job.cancelAndJoin()
        hang = false
        assertEquals(CompletionResult.Success("fine"), backend.complete(testContext(), testConfig()))
    }

    @Test
    fun `missing binary reports CliNotFound`() = runBlocking {
        val none = PersistentClaudeBackend(ClaudeCliLocator(isExecutable = { false }, shellLookup = { null })) { _, _, _ -> error("no") }
        val result = none.complete(testContext(), testConfig(claudePath = ""))
        assertEquals(FailureKind.CliNotFound, assertIs<CompletionResult.Failure>(result).kind)
    }

    @Test
    fun `shutdown kills the process`() = runBlocking {
        val process = echoProcess()
        val backend = backend(process)
        backend.complete(testContext(), testConfig())
        backend.shutdown()
        assertTrue(process.killed)
    }
}
