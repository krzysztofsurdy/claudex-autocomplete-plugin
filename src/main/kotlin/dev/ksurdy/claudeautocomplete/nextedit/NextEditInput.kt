package dev.ksurdy.claudeautocomplete.nextedit

import dev.ksurdy.claudeautocomplete.FileWindow

data class NextEditInput(
    val edits: List<EditHunk>,
    val windowText: String,
    val firstLine: Int,
    val caretLine: Int,
) {
    companion object {
        fun create(
            edits: List<EditHunk>,
            text: CharSequence,
            caretOffset: Int,
            mode: String,
            wholeFileMaxLines: Int,
            linesAround: Int,
            hardCapChars: Int = FileWindow.HARD_CAP_CHARS,
        ): NextEditInput {
            val lines = text.toString().split('\n')
            val caretIndex = text.subSequence(0, caretOffset.coerceIn(0, text.length)).count { it == '\n' }
            val windowed = when (mode) {
                FileWindow.MODE_WHOLE_FILE -> false
                FileWindow.MODE_LINES_AROUND -> true
                else -> lines.size > wholeFileMaxLines
            }
            var radius = if (windowed) linesAround.coerceAtLeast(0) else lines.size
            var range = rangeAround(caretIndex, radius, lines.size)
            while (charCount(lines, range) > hardCapChars && radius > 0) {
                radius /= 2
                range = rangeAround(caretIndex, radius, lines.size)
            }
            return NextEditInput(edits, lines.subList(range.first, range.last + 1).joinToString("\n"), range.first + 1, caretIndex + 1)
        }

        private fun rangeAround(caretIndex: Int, radius: Int, lineCount: Int): IntRange =
            maxOf(0, caretIndex - radius)..minOf(lineCount - 1, caretIndex + radius)

        private fun charCount(lines: List<String>, range: IntRange): Int = range.sumOf { lines[it].length + 1 } - 1
    }
}
