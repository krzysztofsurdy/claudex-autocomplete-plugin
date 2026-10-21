package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

internal object CloserSnip {
    private const val WORDS = "end|endif|endfor|endwhile|endforeach|endswitch|fi|done|esac"
    private val CLOSER_LINE = Regex("""^(?:[)\]}]|(?:$WORDS)\b)[\s)\]};,.]*$""")
    private val WORD_CLOSER = Regex("""^(?:$WORDS)\b""")
    private val OPENER_LINE = Regex(
        """^(?:if|unless|while|until|for|foreach|def|class|module|begin|case|switch|function|fn|do|loop)\b|\b(?:do|then)(?:\s*\|[^|]*\|)?$""",
    )

    fun isCloserLine(trimmed: String): Boolean = CLOSER_LINE.matches(trimmed)

    fun snip(text: String, context: CompletionContext): String? {
        if (!LinesBelow.restOfLineIsBlank(context)) return null
        val following = LinesBelow.following(context)
        if (following.isEmpty()) return null
        val lines = text.lines()
        val run = trailingCloserLines(lines, context.prefix.substringAfterLast('\n').isBlank())
        if (run.isEmpty()) return null
        val budget = Budget(text)
        for (count in minOf(run.size, following.size) downTo 1) {
            val tail = run.takeLast(count).map { lines[it].trim() }
            if (tail == following.take(count) && budget.covers(tail)) {
                return lines.take(run[run.size - count]).joinToString("\n").trimEnd()
            }
        }
        return null
    }

    private fun trailingCloserLines(lines: List<String>, blankCaretLine: Boolean): List<Int> {
        val run = ArrayDeque<Int>()
        for (index in lines.indices.reversed()) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty()) continue
            if (!isCloserLine(trimmed) || (index == 0 && !blankCaretLine)) break
            run.addFirst(index)
        }
        return run
    }

    private class Budget(text: String) {
        private val brackets = Brackets.scan(text).unmatchedByKind
        private val words: Int

        init {
            val trimmed = text.lines().map { it.trim() }
            words = (trimmed.count { WORD_CLOSER.containsMatchIn(it) && isCloserLine(it) } - trimmed.count { OPENER_LINE.containsMatchIn(it) }).coerceAtLeast(0)
        }

        fun covers(closerLines: List<String>): Boolean {
            val perKind = closerLines.joinToString("").filter { it in Brackets.CLOSERS }.groupingBy { it }.eachCount()
            val wordCount = closerLines.count { WORD_CLOSER.containsMatchIn(it) }
            return wordCount <= words && perKind.all { (kind, count) -> count <= (brackets[kind] ?: 0) }
        }
    }
}
