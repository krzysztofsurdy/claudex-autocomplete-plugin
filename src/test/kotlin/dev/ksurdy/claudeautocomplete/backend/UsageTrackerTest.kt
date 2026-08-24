package dev.ksurdy.claudeautocomplete.backend

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageTrackerTest {
    private val now = Instant.ofEpochSecond(100)
    private val event = StreamEvent.RateLimit("allowed", UsageWindow(0.1, null), UsageWindow(0.2, null))

    @Test
    fun `publish stores last usage and notifies listeners`() {
        val tracker = UsageTracker { now }
        val seen = mutableListOf<UsageLimits>()
        tracker.addListener { seen += it }
        assertNull(tracker.last)
        tracker.publish(event)
        val expected = UsageLimits(UsageWindow(0.1, null), UsageWindow(0.2, null), "allowed", now)
        assertEquals(expected, tracker.last)
        assertEquals(listOf(expected), seen)
    }

    @Test
    fun `unsubscribe stops notifications`() {
        val tracker = UsageTracker { now }
        var calls = 0
        val unsubscribe = tracker.addListener { calls++ }
        tracker.publish(event)
        unsubscribe()
        tracker.publish(event)
        assertEquals(1, calls)
    }

    @Test
    fun `throwing listener does not break others`() {
        val tracker = UsageTracker { now }
        var calls = 0
        tracker.addListener { error("boom") }
        tracker.addListener { calls++ }
        tracker.publish(event)
        assertEquals(1, calls)
    }
}
