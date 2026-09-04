package dev.ksurdy.claudeautocomplete.backend

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompletionRouterTest {
    private class FakeBackend(var text: String) : CompletionBackend {
        var calls = 0
        var shutdowns = 0
        override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
            calls++
            return CompletionResult.Success(text)
        }

        override fun shutdown() {
            shutdowns++
        }
    }

    private val claudeTracker = UsageTracker(ProviderKind.Claude)
    private val codexTracker = UsageTracker(ProviderKind.Codex)
    private val claudeFake = FakeBackend("from-claude")
    private var codexExecs = 0

    private fun codexProcess() = FakeClaudeProcess { line ->
        when {
            line == "<EOF>" -> {
                emit("""{"type":"item.completed","item":{"type":"agent_message","text":"from-codex"}}""")
                emit("""{"type":"turn.completed"}""")
                finish()
            }
            line.contains("\"initialize\"") -> emit("""{"id":1,"result":{}}""")
            line.contains("account/rateLimits/read") -> emit("""{"id":2,"result":{"rateLimits":{"primary":{"usedPercent":50}}}}""")
        }
    }

    private val router = CompletionRouter(
        ClaudeCliBackend(claudeFake, FakeBackend("oneshot"), usage = claudeTracker),
        CodexCliBackend(locate = { "/bin/codex" }, usage = codexTracker) { _, _, mode ->
            if (mode == CodexMode.Exec) codexExecs++
            codexProcess()
        },
    )

    private fun config(provider: ProviderKind) = BackendConfig(provider, testConfig(), testCodexConfig())

    @Test
    fun `routes to the selected provider`() = runBlocking {
        assertEquals(CompletionResult.Success("from-claude"), router.complete(testContext(), config(ProviderKind.Claude)))
        assertEquals(CompletionResult.Success("from-codex"), router.complete(testContext(), config(ProviderKind.Codex)))
        assertEquals(1, claudeFake.calls)
        assertEquals(1, codexExecs)
    }

    @Test
    fun `switching to codex shuts down claude persistent process`() = runBlocking {
        router.complete(testContext(), config(ProviderKind.Claude))
        assertEquals(0, claudeFake.shutdowns)
        router.complete(testContext(), config(ProviderKind.Codex))
        assertEquals(1, claudeFake.shutdowns)
        router.complete(testContext(), config(ProviderKind.Codex))
        assertEquals(1, claudeFake.shutdowns)
    }

    @Test
    fun `last usage follows the selected provider`() = runBlocking {
        claudeTracker.publish(StreamEvent.RateLimit("allowed", UsageWindow(0.1, null), null))
        codexTracker.publish(StreamEvent.RateLimit("allowed", UsageWindow(0.9, null), null))
        assertEquals(ProviderKind.Claude, router.lastUsage?.provider)
        router.complete(testContext(), config(ProviderKind.Codex))
        assertEquals(0.9, router.lastUsage?.fiveHour?.utilization)
        assertEquals(ProviderKind.Codex, router.lastUsage?.provider)
    }

    @Test
    fun `last usage is null before any usage`() = assertNull(router.lastUsage)

    @Test
    fun `listeners receive usage from both providers and can unsubscribe`() {
        val seen = mutableListOf<ProviderKind>()
        val unsubscribe = router.addUsageListener { seen += it.provider }
        claudeTracker.publish(StreamEvent.RateLimit("allowed", null, null))
        codexTracker.publish(StreamEvent.RateLimit("allowed", null, null))
        unsubscribe()
        claudeTracker.publish(StreamEvent.RateLimit("allowed", null, null))
        assertEquals(listOf(ProviderKind.Claude, ProviderKind.Codex), seen)
    }

    @Test
    fun `refreshUsage for codex uses app server`() = runBlocking {
        val usage = router.refreshUsage(config(ProviderKind.Codex))
        assertEquals(0.5, usage?.fiveHour?.utilization)
        assertEquals(ProviderKind.Codex, usage?.provider)
        assertEquals(0, codexExecs)
    }

    @Test
    fun `shutdown stops claude`() {
        router.shutdown()
        assertEquals(1, claudeFake.shutdowns)
    }
}
