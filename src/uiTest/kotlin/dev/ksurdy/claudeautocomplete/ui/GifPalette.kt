package dev.ksurdy.claudeautocomplete.ui

import java.awt.image.IndexColorModel
import kotlin.math.sqrt

class ColorHistogram {
    private val counts = HashMap<Int, Int>()

    fun add(pixels: IntArray) {
        for (pixel in pixels) counts.merge(pixel and RGB_MASK, 1, Int::plus)
    }

    fun palette(size: Int = MAX_COLORS): IndexColorModel {
        val boxes = mutableListOf(ColorBox(counts.map { Weighted(it.key, it.value) }))
        while (boxes.size < size) {
            val target = boxes.filter { it.colors.size > 1 }.maxByOrNull { it.priority() } ?: break
            boxes.remove(target)
            boxes += target.split()
        }
        val means = boxes.map { it.mean() }
        return IndexColorModel(
            BITS,
            means.size,
            ByteArray(means.size) { (means[it] shr RED_SHIFT).toByte() },
            ByteArray(means.size) { (means[it] shr GREEN_SHIFT).toByte() },
            ByteArray(means.size) { means[it].toByte() },
        )
    }

    private companion object {
        const val RGB_MASK = 0xFFFFFF
        const val BITS = 8
        const val MAX_COLORS = 256
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}

class Weighted(val rgb: Int, val count: Int)

class ColorBox(val colors: List<Weighted>) {
    private val spans = IntArray(CHANNELS) { channel ->
        val values = colors.map { channelOf(it.rgb, channel) }
        values.max() - values.min()
    }

    fun priority(): Double = sqrt(colors.sumOf { it.count.toDouble() }) * spans.max()

    fun split(): List<ColorBox> {
        val channel = spans.indices.maxBy { spans[it] }
        val sorted = colors.sortedBy { channelOf(it.rgb, channel) }
        val half = sorted.sumOf { it.count.toLong() } / 2
        var running = 0L
        val cut = (sorted.indexOfFirst { running += it.count; running >= half } + 1).coerceIn(1, sorted.size - 1)
        return listOf(ColorBox(sorted.subList(0, cut)), ColorBox(sorted.subList(cut, sorted.size)))
    }

    fun mean(): Int {
        val total = colors.sumOf { it.count.toLong() }
        val parts = IntArray(CHANNELS) { channel ->
            (colors.sumOf { channelOf(it.rgb, channel).toLong() * it.count } / total).toInt()
        }
        return (parts[0] shl RED_SHIFT) or (parts[1] shl GREEN_SHIFT) or parts[2]
    }

    private companion object {
        const val CHANNELS = 3
        const val BYTE = 0xFF
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8

        fun channelOf(rgb: Int, channel: Int): Int = (rgb shr (RED_SHIFT - GREEN_SHIFT * channel)) and BYTE
    }
}
