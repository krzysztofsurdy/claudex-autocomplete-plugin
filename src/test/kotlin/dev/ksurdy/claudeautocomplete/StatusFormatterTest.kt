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
    fun usageSuffixHidesMissingParts() {
        assertEquals(" · 5h 42% · 7d 18%", StatusFormatter.usageSuffix(usage()))
        assertEquals(" · 7d 18%", StatusFormatter.usageSuffix(usage(five = null)))
        assertEquals("", StatusFormatter.usageSuffix(usage(five = null, seven = null)))
        assertEquals("", StatusFormatter.usageSuffix(null))
    }

    @Test
    fun warnsAtEightyPercent() {
        assertFalse(StatusFormatter.isWarning(usage()))
        assertTrue(StatusFormatter.isWarning(usage(seven = 0.8)))
        assertTrue(StatusFormatter.widgetText(true, ClaudeStatus.Ready, usage(five = 0.9), true, 0).startsWith("⚠ Claude: Ready"))
    }

    @Test
    fun statusLabels() {
        val t = 10_000L
        assertEquals("Off", StatusFormatter.statusLabel(false, ClaudeStatus.Thinking(0), t))
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
    fun widgetTextAppendsUsageOnlyWhenEnabledInSettings() {
        assertEquals("Claude: Ready · 5h 42% · 7d 18%", StatusFormatter.widgetText(true, ClaudeStatus.Ready, usage(), true, 0))
        assertEquals("Claude: Ready", StatusFormatter.widgetText(true, ClaudeStatus.Ready, usage(), false, 0))
        assertEquals("Claude: Ready", StatusFormatter.widgetText(true, ClaudeStatus.Ready, null, true, 0))
    }

    @Test
    fun animatesOnlyWhileActive() {
        assertTrue(StatusFormatter.isAnimating(true, ClaudeStatus.Thinking(0), 99_999))
        assertTrue(StatusFormatter.isAnimating(true, ClaudeStatus.Done(1, 1_000), 2_000))
        assertFalse(StatusFormatter.isAnimating(true, ClaudeStatus.Done(1, 1_000), 9_000))
        assertFalse(StatusFormatter.isAnimating(false, ClaudeStatus.Thinking(0), 0))
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
    fun providerNameIsUsedInWidgetTextAndTooltip() {
        assertEquals("Codex: Ready", StatusFormatter.widgetText(true, ClaudeStatus.Ready, null, true, 0, "Codex"))
        assertTrue(StatusFormatter.tooltip(true, ClaudeStatus.Ready, null, null, null, now, zone, "Codex").contains("Provider: Codex"))
    }

    @Test
    fun codexUsesWeeklyLabel() {
        val codex = usage().copy(provider = dev.ksurdy.claudeautocomplete.backend.ProviderKind.Codex)
        assertEquals(" \u00B7 5h 42% \u00B7 wk 18%", StatusFormatter.usageSuffix(codex))
        assertTrue(StatusFormatter.tooltip(true, ClaudeStatus.Ready, codex, null, null, now, zone, "Codex").contains("weekly: 18%"))
    }

    @Test
    fun windowLabelsComeFromWindowMinutes() {
        val codex = UsageLimits(UsageWindow(0.1, null, 300), UsageWindow(0.2, null, 10_080), null, now, dev.ksurdy.claudeautocomplete.backend.ProviderKind.Codex)
        assertEquals(" \u00B7 5h 10% \u00B7 wk 20%", StatusFormatter.usageSuffix(codex))
        val odd = UsageLimits(UsageWindow(0.1, null, 120), UsageWindow(0.2, null, 4_320), null, now)
        assertEquals(" \u00B7 2h 10% \u00B7 3d 20%", StatusFormatter.usageSuffix(odd))
    }
}
