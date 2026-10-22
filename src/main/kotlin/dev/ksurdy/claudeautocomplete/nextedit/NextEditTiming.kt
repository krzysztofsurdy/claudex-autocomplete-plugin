package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.math.roundToLong

enum class SkipReason(val label: String) {
    Cooldown("cooldown"),
    LineCooldown("line cooldown"),
    Remembered("rejection memory"),
    InlineCompletion("inline completion"),
}

class NextEditTiming(private val clock: () -> Long) {
    private var adaptiveMs: Double? = null
    private var lastRejectionAt: Long? = null
    private val rejectedLines = HashMap<Int, Long>()

    @Synchronized
    fun pauseMs(baseMs: Long): Long = current(baseMs).roundToLong()

    @Synchronized
    fun recordRejection(line: Int, baseMs: Long) {
        adaptiveMs = (current(baseMs) * REJECTION_FACTOR).coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
        val now = clock()
        lastRejectionAt = now
        rejectedLines[line] = now
    }

    @Synchronized
    fun recordAcceptance(baseMs: Long) {
        adaptiveMs = (current(baseMs) * ACCEPTANCE_FACTOR).coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
    }

    @Synchronized
    fun requestBlock(): SkipReason? = if (within(lastRejectionAt, REJECTION_COOLDOWN_MS)) SkipReason.Cooldown else null

    @Synchronized
    fun lineBlock(line: Int): SkipReason? = if (within(rejectedLines[line], LINE_COOLDOWN_MS)) SkipReason.LineCooldown else null

    fun extraWaitMs(lineText: String, caretColumn: Int): Long =
        if (lineText.isNotBlank() && caretColumn >= lineText.length) END_OF_LINE_EXTRA_MS else 0

    private fun current(baseMs: Long): Double = (adaptiveMs ?: baseMs.toDouble()).coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)

    private fun within(at: Long?, windowMs: Long): Boolean = at != null && clock() - at < windowMs

    companion object {
        const val REJECTION_FACTOR = 1.5
        const val ACCEPTANCE_FACTOR = 0.8
        const val REJECTION_COOLDOWN_MS = 5_000L
        const val LINE_COOLDOWN_MS = 30_000L
        const val END_OF_LINE_EXTRA_MS = 2_000L
        private const val MIN_PAUSE_MS = 50.0
        private const val MAX_PAUSE_MS = 3_000.0
    }
}
