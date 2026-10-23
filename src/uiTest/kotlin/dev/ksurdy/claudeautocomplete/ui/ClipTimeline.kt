package dev.ksurdy.claudeautocomplete.ui

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class ClipTimeline(
    private val holdFirstMs: Long = 1500,
    private val holdLastMs: Long = 1500,
    private val idleCapMs: Long = 1200,
    private val statusBarHeight: Int = 40,
) {
    private class Kept(val recorded: RecordedFrame, val at: Long)

    fun select(recorded: List<RecordedFrame>): List<GifFrame> {
        require(recorded.isNotEmpty()) { "nothing was recorded" }
        val kept = mutableListOf<Kept>()
        var reference: IntArray? = null
        var clock = 0L
        var idleMs = 0L
        var previousAt = recorded.first().elapsedMs
        for (frame in recorded) {
            val delta = frame.elapsedMs - previousAt
            previousAt = frame.elapsedMs
            val image = ImageIO.read(frame.file.toFile())
            val pixels = GifWriter.pixels(image)
            val before = reference
            if (before != null) {
                val idle = isIdle(before, pixels, image)
                idleMs = if (idle) idleMs + delta else 0
                if (idle && idleMs > idleCapMs) continue
                clock += delta
            }
            kept += Kept(frame, clock)
            reference = pixels
        }
        return kept.mapIndexed { index, frame ->
            val next = kept.getOrNull(index + 1)
            val delay = if (next == null) holdLastMs else (next.at - frame.at).coerceAtLeast(MIN_DELAY_MS)
            GifFrame(frame.recorded.file, if (index == 0) delay + holdFirstMs else delay)
        }
    }

    private fun isIdle(before: IntArray, after: IntArray, image: BufferedImage): Boolean {
        val firstStatusRow = image.height - statusBarHeight
        for (index in after.indices) {
            if (before[index] != after[index] && index / image.width < firstStatusRow) return false
        }
        return true
    }

    private companion object {
        const val MIN_DELAY_MS = 40L
    }
}
