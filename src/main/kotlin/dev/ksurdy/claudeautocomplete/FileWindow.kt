package dev.ksurdy.claudeautocomplete

data class FileWindow(
    val prefix: String,
    val suffix: String,
    val prefixTruncated: Boolean,
    val suffixTruncated: Boolean,
) {
    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_WHOLE_FILE = "wholeFile"
        const val MODE_LINES_AROUND = "linesAround"
        const val HARD_CAP_CHARS = 200_000

        private const val PREFIX_SHARE_PERCENT = 60

        fun compute(
            text: CharSequence,
            offset: Int,
            mode: String,
            wholeFileMaxLines: Int,
            linesAround: Int,
            hardCapChars: Int = HARD_CAP_CHARS,
        ): FileWindow {
            val caret = offset.coerceIn(0, text.length)
            val windowed = when (mode) {
                MODE_WHOLE_FILE -> false
                MODE_LINES_AROUND -> true
                else -> countLines(text) > wholeFileMaxLines
            }
            var start = 0
            var end = text.length
            if (windowed) {
                start = lineStartAbove(text, caret, linesAround.coerceAtLeast(0))
                end = lineEndBelow(text, caret, linesAround.coerceAtLeast(0))
            }
            val prefixCap = hardCapChars * PREFIX_SHARE_PERCENT / 100
            val suffixCap = hardCapChars - prefixCap
            val clippedStart = maxOf(start, caret - prefixCap)
            val clippedEnd = minOf(end, caret + suffixCap)
            return FileWindow(
                prefix = text.subSequence(clippedStart, caret).toString(),
                suffix = text.subSequence(caret, clippedEnd).toString(),
                prefixTruncated = clippedStart > 0,
                suffixTruncated = clippedEnd < text.length,
            )
        }

        private fun countLines(text: CharSequence): Int = 1 + text.count { it == '\n' }

        private fun lineStartAbove(text: CharSequence, caret: Int, lines: Int): Int {
            var index = caret
            var remaining = lines + 1
            while (index > 0) {
                if (text[index - 1] == '\n') {
                    remaining--
                    if (remaining == 0) return index
                }
                index--
            }
            return 0
        }

        private fun lineEndBelow(text: CharSequence, caret: Int, lines: Int): Int {
            var index = caret
            var remaining = lines + 1
            while (index < text.length) {
                if (text[index] == '\n') {
                    remaining--
                    if (remaining == 0) return if (index > 0 && text[index - 1] == '\r') index - 1 else index
                }
                index++
            }
            return text.length
        }
    }
}
