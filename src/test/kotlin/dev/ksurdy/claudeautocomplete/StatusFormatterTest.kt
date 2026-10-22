package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.UsageLimits
import dev.ksurdy.claudeautocomplete.backend.UsageWindow
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusFormatterTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun usage(five: Double? = 0.42, seven: Double? = 0.18, resetsAt: Instant? = now.plus(Duration.ofMinutes(134))) =
        UsageLimits(five?.let { UsageWindow(it, resetsAt) }, seven?.let { UsageWindow(it, null) }, "allowed", now)

    @Test
    fun formatsPercentAndLatency() {
        assertEquals("42%", StatusFormatter.percent(0.42))
        assertEquals("0%", StatusFormatter.percent(-0.2))
        assertEquals("0.7s", StatusFormatter.latency(700))
    }

    @Test
    fun formatsDurations() {
        assertEquals("45s", StatusFormatter.duration(Duration.ofSeconds(45)))
        assertEquals("14m", StatusFormatter.duration(Duration.ofMinutes(14)))
        assertEquals("2h 14m", StatusFormatter.duration(Duration.ofMinutes(134)))
        assertEquals("3d 4h", StatusFormatter.duration(Duration.ofHours(76)))
    }

    @Test
    fun formatsResetText() {
        assertEquals("resets in 2h 14m, at 14:14", StatusFormatter.resets(now.plus(Duration.ofMinutes(134)), now, zone))
        assertEquals("reset due", StatusFormatter.resets(now.minusSeconds(5), now, zone))
        assertEquals(null, StatusFormatter.resets(null, now, zone))
    }

    @Test
    fun statusLabels() {
        val t = 10_000L
        assertEquals("Disabled", StatusFormatter.statusLabel(false, ClaudeStatus.Thinking(0), t))
        assertEquals("Ready", StatusFormatter.statusLabel(true, ClaudeStatus.Ready, t))
        assertEquals("Waiting…", StatusFormatter.statusLabel(true, ClaudeStatus.Waiting(t - 100), t))
        assertEquals("Ready", StatusFormatter.statusLabel(true, ClaudeStatus.Waiting(t - 5_000), t))
        assertEquals("Thinking… 2s", StatusFormatter.statusLabel(true, ClaudeStatus.Thinking(t - 2_100), t))
        assertEquals("0.7s", StatusFormatter.statusLabel(true, ClaudeStatus.Done(700, t - 1_000), t))
        assertEquals("Ready", StatusFormatter.statusLabel(true, ClaudeStatus.Done(700, t - 9_000), t))
        assertEquals("Error", StatusFormatter.statusLabel(true, ClaudeStatus.Error("x"), t))
        assertEquals("Limit reached", StatusFormatter.statusLabel(true, ClaudeStatus.LimitReached(null), t))
    }

    @Test
    fun tooltipListsDetails() {
        val tip = StatusFormatter.tooltip(true, ClaudeStatus.Ready, usage(), 700, "haiku", now, zone)
        assertTrue(tip.contains("5h: 42% (resets in 2h 14m, at 14:14)"), tip)
        assertTrue(tip.contains("7d: 18%"), tip)
        assertTrue(tip.contains("Status: allowed"), tip)
        assertTrue(tip.contains("Usage updated: 12:00"), tip)
        assertTrue(tip.contains("Last request: 0.7s"), tip)
        assertTrue(tip.contains("Model: haiku"), tip)
        assertTrue(tip.contains("Provider: Claude"), tip)
        assertTrue(tip.contains("State: Ready"), tip)
    }

    @Test
    fun tooltipStateFollowsTheStatus() {
        fun tip(status: ClaudeStatus) = StatusFormatter.tooltip(true, status, null, null, null, now, zone)
        val nowMs = now.toEpochMilli()
        assertTrue(tip(ClaudeStatus.Waiting(nowMs - 100)).contains("State: Waiting…"))
        assertTrue(tip(ClaudeStatus.Thinking(nowMs - 2_100)).contains("State: Thinking… 2s"))
        assertTrue(tip(ClaudeStatus.Done(700, nowMs - 1_000)).contains("State: Done"))
        assertTrue(tip(ClaudeStatus.Error("not logged in")).contains("Error: not logged in"))
        assertTrue(tip(ClaudeStatus.LimitReached(null)).contains("Usage limit reached"))
    }

    @Test
    fun limitResetPrefersExhaustedWindow() {
        val later = now.plus(Duration.ofHours(30))
        val limits = UsageLimits(UsageWindow(1.0, now.plusSeconds(600)), UsageWindow(1.0, later), null, now)
        assertEquals(later, StatusFormatter.limitResetsAt(limits, now))
        assertEquals(now.plus(Duration.ofMinutes(5)), StatusFormatter.limitResetsAt(null, now))
        assertEquals(now.plusSeconds(600), StatusFormatter.limitResetsAt(usage(resetsAt = now.plusSeconds(600)), now))
    }

    @Test
    fun usageIsStaleWhenUnknownOrOlderThanTenMinutes() {
        assertTrue(StatusFormatter.usageIsStale(null, null, now))
        assertFalse(StatusFormatter.usageIsStale(usage(), null, now.plusSeconds(599)))
        assertTrue(StatusFormatter.usageIsStale(usage(), null, now.plusSeconds(600)))
        assertFalse(StatusFormatter.usageIsStale(usage(), now.plusSeconds(500), now.plusSeconds(700)))
        assertFalse(StatusFormatter.usageIsStale(null, now, now.plusSeconds(60)))
    }

    @Test
    fun providerNameIsUsedInTooltip() {
        assertTrue(StatusFormatter.tooltip(true, ClaudeStatus.Ready, null, null, null, now, zone, "Codex").contains("Provider: Codex"))
    }

    @Test
    fun codexUsesWeeklyLabel() {
        val codex = usage().copy(provider = dev.ksurdy.claudeautocomplete.backend.ProviderKind.Codex)
        assertTrue(StatusFormatter.tooltip(true, ClaudeStatus.Ready, codex, null, null, now, zone, "Codex").contains("weekly: 18%"))
    }

    @Test
    fun tooltipInvitesEnablingBeforeConsent() {
        val tip = StatusFormatter.tooltip(false, ClaudeStatus.Ready, null, null, null, now, zone, "Claudex", consentGiven = false)
        assertTrue(tip.contains("Click to enable completions"), tip)
        val off = StatusFormatter.tooltip(false, ClaudeStatus.Ready, null, null, null, now, zone, "Claude", consentGiven = true)
        assertTrue(off.contains("Completions are off"), off)
    }

    @Test
    fun windowLabelsComeFromWindowMinutes() {
        val codex = UsageLimits(UsageWindow(0.1, null, 300), UsageWindow(0.2, null, 10_080), null, now, dev.ksurdy.claudeautocomplete.backend.ProviderKind.Codex)
        val codexTip = StatusFormatter.tooltip(true, ClaudeStatus.Ready, codex, null, null, now, zone, "Codex")
        assertTrue(codexTip.contains("5h: 10%"), codexTip)
        assertTrue(codexTip.contains("weekly: 20%"), codexTip)
        val odd = UsageLimits(UsageWindow(0.1, null, 120), UsageWindow(0.2, null, 4_320), null, now)
        val oddTip = StatusFormatter.tooltip(true, ClaudeStatus.Ready, odd, null, null, now, zone)
        assertTrue(oddTip.contains("2h: 10%"), oddTip)
        assertTrue(oddTip.contains("3d: 20%"), oddTip)
    }
}
