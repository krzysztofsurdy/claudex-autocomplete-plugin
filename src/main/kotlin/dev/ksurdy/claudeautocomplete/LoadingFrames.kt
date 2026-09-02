package dev.ksurdy.claudeautocomplete

import java.util.Locale

object LoadingFrames {
    const val SHOW_DELAY_MS = 150
    const val FRAME_INTERVAL_MS = 80
    const val ELAPSED_THRESHOLD_MS = 1500L
    const val WHOLE_SECONDS_FROM_MS = 10_000L
    const val WIDEST_ELAPSED = " 99.9s"

    val BRAILLE = listOf("⢎⡰", "⢎⡡", "⢎⡑", "⢎⠱", "⠎⡱", "⢊⡱", "⢌⡱", "⢆⡱")
    val DOTS = listOf("·  ", "·· ", "···")

    fun frames(braille: Boolean): List<String> = if (braille) BRAILLE else DOTS

    fun frame(tick: Int, braille: Boolean): String = frames(braille).let { it[Math.floorMod(tick, it.size)] }

    fun elapsed(millis: Long): String? = when {
        millis < ELAPSED_THRESHOLD_MS -> null
        millis < WHOLE_SECONDS_FROM_MS -> String.format(Locale.ROOT, " %.1fs", millis / 1000.0)
        else -> " ${millis / 1000}s"
    }

    fun text(tick: Int, elapsedMs: Long, braille: Boolean): String = frame(tick, braille) + elapsed(elapsedMs).orEmpty()
}
