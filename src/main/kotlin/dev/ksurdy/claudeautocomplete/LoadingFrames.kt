package dev.ksurdy.claudeautocomplete

object LoadingFrames {
    const val SHOW_DELAY_MS = 150
    const val FRAME_INTERVAL_MS = 300

    private val FRAMES = listOf("·  ", "·· ", "···")

    val widest: String = FRAMES.last()

    fun frame(tick: Int): String = FRAMES[Math.floorMod(tick, FRAMES.size)]
}
