package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.UsageLimits
import dev.ksurdy.claudeautocomplete.backend.UsageWindow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object StatusFormatter {
    const val WARNING_THRESHOLD = 0.8
    const val WAITING_EXPIRY_MS = 3_000L
    const val DONE_DISPLAY_MS = 4_000L
    const val DEFAULT_BLOCK_MINUTES = 5L
    const val USAGE_REFRESH_MINUTES = 10L

    fun percent(utilization: Double): String = "${(utilization * 100).roundToInt().coerceAtLeast(0)}%"

    fun latency(millis: Long): String = String.format(Locale.ROOT, "%.1fs", millis / 1000.0)

    fun duration(duration: Duration): String {
        val seconds = duration.seconds.coerceAtLeast(0)
        val days = seconds / 86_400
        val hours = seconds % 86_400 / 3_600
        val minutes = seconds % 3_600 / 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun clock(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone).format(instant)

    fun resets(resetsAt: Instant?, now: Instant, zone: ZoneId): String? {
        resetsAt ?: return null
        if (!resetsAt.isAfter(now)) return "reset due"
        return "resets in ${duration(Duration.between(now, resetsAt))}, at ${clock(resetsAt, zone)}"
    }

    fun isWarning(usage: UsageLimits?): Boolean =
        listOfNotNull(usage?.fiveHour, usage?.sevenDay).any { it.utilization >= WARNING_THRESHOLD }

    fun usageSuffix(usage: UsageLimits?): String {
        val parts = listOfNotNull(
            usage?.fiveHour?.let { "5h ${percent(it.utilization)}" },
            usage?.sevenDay?.let { "7d ${percent(it.utilization)}" },
        )
        return if (parts.isEmpty()) "" else " · " + parts.joinToString(" · ")
    }

    fun statusLabel(enabled: Boolean, status: ClaudeStatus, nowMs: Long): String = when {
        !enabled -> "Off"
        else -> when (status) {
            ClaudeStatus.Ready -> "Ready"
            is ClaudeStatus.Waiting -> if (nowMs - status.since < WAITING_EXPIRY_MS) "Waiting…" else "Ready"
            is ClaudeStatus.Thinking -> "Thinking… ${((nowMs - status.since) / 1000).coerceAtLeast(0)}s"
            is ClaudeStatus.Done -> if (nowMs - status.at < DONE_DISPLAY_MS) latency(status.latencyMs) else "Ready"
            is ClaudeStatus.Error -> "Error"
            is ClaudeStatus.LimitReached -> "Limit reached"
        }
    }

    fun isAnimating(enabled: Boolean, status: ClaudeStatus, nowMs: Long): Boolean = enabled && when (status) {
        is ClaudeStatus.Thinking -> true
        is ClaudeStatus.Waiting -> nowMs - status.since < WAITING_EXPIRY_MS
        is ClaudeStatus.Done -> nowMs - status.at < DONE_DISPLAY_MS
        else -> false
    }

    fun widgetText(
        enabled: Boolean,
        status: ClaudeStatus,
        usage: UsageLimits?,
        showUsage: Boolean,
        nowMs: Long,
    ): String {
        val warning = (showUsage && isWarning(usage)) || status is ClaudeStatus.LimitReached
        val prefix = if (warning && enabled) "⚠ " else ""
        val suffix = if (showUsage) usageSuffix(usage) else ""
        return "${prefix}Claude: ${statusLabel(enabled, status, nowMs)}$suffix"
    }

    fun tooltip(
        enabled: Boolean,
        status: ClaudeStatus,
        usage: UsageLimits?,
        lastLatencyMs: Long?,
        model: String?,
        now: Instant,
        zone: ZoneId,
    ): String {
        val lines = ArrayList<String>()
        when {
            !enabled -> lines += "Disabled"
            status is ClaudeStatus.Error -> lines += "Error: ${status.message}"
            status is ClaudeStatus.LimitReached -> lines += "Usage limit reached" +
                (resets(status.resetsAt, now, zone)?.let { " ($it)" } ?: "")
        }
        usage?.let {
            it.fiveHour?.let { w -> lines += windowLine("5h", w, now, zone) }
            it.sevenDay?.let { w -> lines += windowLine("7d", w, now, zone) }
            it.status?.let { s -> lines += "Status: $s" }
            lines += "Usage updated: ${clock(it.observedAt, zone)}"
        }
        lastLatencyMs?.let { lines += "Last request: ${latency(it)}" }
        model?.takeIf { it.isNotEmpty() }?.let { lines += "Model: $it" }
        lines += "Click for menu"
        return lines.joinToString("<br>", "<html>", "</html>")
    }

    fun usageIsStale(usage: UsageLimits?, lastAttempt: Instant?, now: Instant): Boolean {
        val reference = listOfNotNull(usage?.observedAt, lastAttempt).maxOrNull() ?: return true
        return Duration.between(reference, now) >= Duration.ofMinutes(USAGE_REFRESH_MINUTES)
    }

    fun limitResetsAt(usage: UsageLimits?, now: Instant): Instant {
        val exhausted = listOfNotNull(usage?.fiveHour, usage?.sevenDay)
            .filter { it.utilization >= 1.0 }
            .mapNotNull { it.resetsAt }
            .filter { it.isAfter(now) }
        return exhausted.maxOrNull()
            ?: listOfNotNull(usage?.fiveHour?.resetsAt, usage?.sevenDay?.resetsAt).filter { it.isAfter(now) }.minOrNull()
            ?: now.plus(Duration.ofMinutes(DEFAULT_BLOCK_MINUTES))
    }

    private fun windowLine(label: String, window: UsageWindow, now: Instant, zone: ZoneId): String =
        "$label: ${percent(window.utilization)}" + (resets(window.resetsAt, now, zone)?.let { " ($it)" } ?: "")
}
