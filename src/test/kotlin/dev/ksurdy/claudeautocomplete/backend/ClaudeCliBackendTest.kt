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

    private val timeout = CompletionResult.Failure(FailureKind.Timeout, "slow")

    @Test
    fun `timeouts do not open the breaker`() = runBlocking {
        val persistent = recording(timeout, timeout, timeout)
        val oneShot = recording()
        val backend = ClaudeCliBackend(persistent, oneShot)
        repeat(3) { backend.complete(testContext(), testConfig()) }
        assertEquals(3, persistent.calls)
        assertEquals(0, oneShot.calls)
    }

    @Test
    fun `persistent is retried after cooldown and success closes the breaker`() = runBlocking {
        var now = 0L
        val persistent = recording(other, other, CompletionResult.Success("back"))
        val oneShot = recording()
        val backend = ClaudeCliBackend(persistent, oneShot, clock = { now }, cooldownMs = 60_000)
        repeat(2) { backend.complete(testContext(), testConfig()) }
        now = 30_000
        backend.complete(testContext(), testConfig())
        assertEquals(1, oneShot.calls)
        now = 61_000
        assertEquals(CompletionResult.Success("back"), backend.complete(testContext(), testConfig()))
        backend.complete(testContext(), testConfig())
        assertEquals(4, persistent.calls)
        assertEquals(1, oneShot.calls)
    }

    @Test
    fun `failed half open probe restarts the cooldown`() = runBlocking {
        var now = 0L
        val persistent = recording(other, other, other)
        val oneShot = recording()
        val backend = ClaudeCliBackend(persistent, oneShot, clock = { now }, cooldownMs = 60_000)
        repeat(2) { backend.complete(testContext(), testConfig()) }
        now = 61_000
        backend.complete(testContext(), testConfig())
        now = 100_000
        backend.complete(testContext(), testConfig())
        assertEquals(3, persistent.calls)
        assertEquals(1, oneShot.calls)
    }

    @Test
    fun `exposes usage from shared tracker`() {
        val tracker = UsageTracker()
        val backend = ClaudeCliBackend(recording(), recording(), usage = tracker)
        var seen: UsageLimits? = null
        val unsubscribe = backend.addUsageListener { seen = it }
        tracker.publish(StreamEvent.RateLimit("allowed", null, UsageWindow(0.4, null)))
        assertEquals(0.4, backend.lastUsage?.sevenDay?.utilization)
        assertEquals(0.4, seen?.sevenDay?.utilization)
        unsubscribe()
    }

    private class PublishingOneShot(private val tracker: UsageTracker, private val result: CompletionResult, private val publish: Boolean) :
        CompletionBackend {
        var lastContext: CompletionContext? = null
        override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
            lastContext = context
            if (publish) tracker.publish(StreamEvent.RateLimit("allowed", UsageWindow(0.7, null), null))
            return result
        }

        override fun shutdown() = Unit
    }

    @Test
    fun `refreshUsage runs a tiny one shot request and returns fresh usage`() = runBlocking {
        val tracker = UsageTracker()
        val persistent = recording()
        val oneShot = PublishingOneShot(tracker, CompletionResult.Empty, publish = true)
        val backend = ClaudeCliBackend(persistent, oneShot, usage = tracker)
        val usage = backend.refreshUsage(testConfig())
        assertEquals(0.7, usage?.fiveHour?.utilization)
        assertEquals(0, persistent.calls)
        assertEquals(false, oneShot.lastContext?.multiline)
        assertEquals(true, (oneShot.lastContext?.prefix?.length ?: Int.MAX_VALUE) < 20)
    }

    @Test
    fun `refreshUsage returns null when the request fails`() = runBlocking {
        val tracker = UsageTracker()
        val failing = PublishingOneShot(tracker, other, publish = false)
        assertEquals(null, ClaudeCliBackend(recording(), failing, usage = tracker).refreshUsage(testConfig()))
    }

    @Test
    fun `refreshUsage returns null when no event arrives even if older usage exists`() = runBlocking {
        val tracker = UsageTracker()
        tracker.publish(StreamEvent.RateLimit("allowed", null, null))
        val quiet = PublishingOneShot(tracker, CompletionResult.Empty, publish = false)
        assertEquals(null, ClaudeCliBackend(recording(), quiet, usage = tracker).refreshUsage(testConfig()))
    }
}
