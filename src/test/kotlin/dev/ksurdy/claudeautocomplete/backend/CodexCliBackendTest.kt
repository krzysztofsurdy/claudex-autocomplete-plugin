package dev.ksurdy.claudeautocomplete.backend

import dev.ksurdy.claudeautocomplete.backend.FakeClaudeProcess.Companion.quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodexCliBackendTest {
    private fun agent(text: String) = """{"type":"item.completed","item":{"id":"i","type":"agent_message","text":${quote(text)}}}"""
    private val completed = """{"type":"turn.completed","usage":{}}"""

    private fun backend(
        process: FakeClaudeProcess,
        usage: UsageTracker = UsageTracker(ProviderKind.Codex),
        modes: MutableList<CodexMode> = mutableListOf(),
    ) = CodexCliBackend(locate = { it.ifBlank { "/bin/codex" } }, usage = usage) { _, _, mode ->
        modes += mode
        process
    }

    private fun answering(vararg lines: String) = FakeClaudeProcess { if (it == "<EOF>") { lines.forEach(::emit); finish() } }

    @Test
    fun `sends prompt on stdin and returns agent message`() = runBlocking {
        val process = answering("""{"type":"thread.started","thread_id":"t"}""", agent("\$a + \$b;"), completed)
        val modes = mutableListOf<CodexMode>()
        val result = backend(process, modes = modes).complete(testContext(), testCodexConfig())
        assertEquals(CompletionResult.Success("\$a + \$b;"), result)
        assertContains(process.sent[0], "<CURSOR/>")
        assertTrue(process.inputClosed)
        assertEquals(listOf(CodexMode.Exec), modes)
    }

    @Test
    fun `last agent message wins`() = runBlocking {
        val result = backend(answering(agent("first"), agent("second"), completed)).complete(testContext(), testCodexConfig())
        assertEquals(CompletionResult.Success("second"), result)
    }

    @Test
    fun `blank message is Empty`() = runBlocking {
        assertEquals(CompletionResult.Empty, backend(answering(agent(""), completed)).complete(testContext(), testCodexConfig()))
    }

    @Test
    fun `error item warning does not fail the request`() = runBlocking {
        val warning = """{"type":"item.completed","item":{"id":"w","type":"error","message":"Model metadata not found"}}"""
        assertEquals(CompletionResult.Success("x"), backend(answering(warning, agent("x"), completed)).complete(testContext(), testCodexConfig()))
    }

    private fun failure(vararg lines: String): CompletionResult.Failure {
        val result = runBlocking { backend(answering(*lines)).complete(testContext(), testCodexConfig()) }
        return assertIs<CompletionResult.Failure>(result)
    }

    @Test
    fun `turn failed with 401 maps to NotLoggedIn on codex provider`() {
        val failure = failure("""{"type":"turn.failed","error":{"message":"unexpected status 401 Unauthorized: Missing bearer or basic authentication"}}""")
        assertEquals(FailureKind.NotLoggedIn, failure.kind)
        assertEquals(ProviderKind.Codex, failure.provider)
    }

    @Test
    fun `usage limit maps to RateLimited`() {
        assertEquals(
            FailureKind.RateLimited,
            failure("""{"type":"turn.failed","error":{"message":"You've hit your usage limit. Try again later."}}""").kind,
        )
    }

    @Test
    fun `unknown model maps to InvalidModel`() {
        assertEquals(
            FailureKind.InvalidModel,
            failure("""{"type":"turn.failed","error":{"message":"The model `gpt-9` does not exist or you do not have access to it."}}""").kind,
        )
    }

    @Test
    fun `no events at all is Other`() = assertEquals(FailureKind.Other, failure().kind)

    @Test
    fun `reconnecting 401 fails fast as NotLoggedIn and kills process`() = runBlocking {
        val process = FakeClaudeProcess { if (it == "<EOF>") emit("""{"type":"error","message":"Reconnecting... 2/5 (unexpected status 401 Unauthorized: Missing bearer)"}""") }
        val result = backend(process).complete(testContext(), testCodexConfig())
        assertEquals(FailureKind.NotLoggedIn, assertIs<CompletionResult.Failure>(result).kind)
        assertTrue(process.killed)
    }

    @Test
    fun `timeout kills process`() = runBlocking {
        val process = FakeClaudeProcess()
        val result = backend(process).complete(testContext(), testCodexConfig(requestTimeoutMs = 100))
        assertEquals(FailureKind.Timeout, assertIs<CompletionResult.Failure>(result).kind)
        assertTrue(process.killed)
    }

    @Test
    fun `cancellation kills process`() = runBlocking {
        val process = FakeClaudeProcess()
        val job = async(Dispatchers.Default) { backend(process).complete(testContext(), testCodexConfig()) }
        while (!process.inputClosed) delay(10)
        job.cancelAndJoin()
        assertTrue(process.killed)
    }

    @Test
    fun `missing binary reports CliNotFound`() = runBlocking {
        val none = CodexCliBackend(locate = { null }) { _, _, _ -> error("no") }
        val failure = assertIs<CompletionResult.Failure>(none.complete(testContext(), testCodexConfig(codexPath = "")))
        assertEquals(FailureKind.CliNotFound, failure.kind)
        assertEquals(ProviderKind.Codex, failure.provider)
    }

    private fun appServer(rateLimitsReply: String) = FakeClaudeProcess { line ->
        when {
            line.contains("\"initialize\"") -> emit("""{"id":1,"result":{"userAgent":"x"}}""")
            line.contains("account/rateLimits/read") -> emit(rateLimitsReply)
        }
    }

    @Test
    fun `refreshUsage reads rate limits through app server and publishes`() = runBlocking {
        val reply = """{"id":2,"result":{"rateLimits":{"primary":{"usedPercent":30,"resetsAt":100},"secondary":{"usedPercent":40}}}}"""
        val process = appServer(reply)
        val tracker = UsageTracker(ProviderKind.Codex)
        val modes = mutableListOf<CodexMode>()
        val usage = backend(process, tracker, modes).refreshUsage(testCodexConfig())
        assertEquals(0.3, usage?.fiveHour?.utilization)
        assertEquals(0.4, usage?.sevenDay?.utilization)
        assertEquals(ProviderKind.Codex, usage?.provider)
        assertEquals(usage, tracker.last)
        assertEquals(listOf(CodexMode.AppServer), modes)
        assertTrue(process.killed)
        assertTrue(process.sent.any { it.contains("\"initialized\"") })
    }

    @Test
    fun `refreshUsage returns null on error response`() = runBlocking {
        val process = appServer("""{"id":2,"error":{"code":-32600,"message":"codex account authentication required to read rate limits"}}""")
        assertNull(backend(process).refreshUsage(testCodexConfig()))
    }

    @Test
    fun `refreshUsage returns null on timeout`() = runBlocking {
        assertNull(backend(FakeClaudeProcess()).refreshUsage(testCodexConfig(requestTimeoutMs = 100)))
    }
}
