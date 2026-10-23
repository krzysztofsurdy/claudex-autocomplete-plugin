package dev.ksurdy.claudeautocomplete.completion

internal object Brackets {
    const val OPENERS = "([{"
    const val CLOSERS = ")]}"

    class Scan(val depth: Int, val unmatchedByKind: Map<Char, Int>) {
        val unmatched: Int get() = unmatchedByKind.values.sum()
    }

    fun scan(text: String): Scan {
        var depth = 0
        val unmatched = mutableMapOf<Char, Int>()
        var quote: Char? = null
        var escaped = false
        for (char in text) {
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
                continue
            }
            when (char) {
                '"', '\'', '`' -> quote = char
                in OPENERS -> depth++
                in CLOSERS -> if (depth > 0) depth-- else unmatched.merge(char, 1, Int::plus)
            }
        }
        return Scan(depth, unmatched)
    }

    fun firstUnmatchedIndex(text: String, closer: Char): Int {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for ((index, char) in text.withIndex()) {
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
                continue
            }
            when (char) {
                '"', '\'', '`' -> quote = char
                in OPENERS -> depth++
                in CLOSERS -> if (depth > 0) depth-- else if (char == closer) return index
            }
        }
        return -1
    }

    fun balance(line: String): Int {
        val scan = scan(substringBeforeLineComment(line))
        return scan.depth - scan.unmatched
    }

    fun substringBeforeLineComment(line: String): String {
        var quote: Char? = null
        var escaped = false
        for ((index, char) in line.withIndex()) {
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
                continue
            }
            when {
                char == '"' || char == '\'' || char == '`' -> quote = char
                char == '/' && line.getOrNull(index + 1) == '/' -> return line.substring(0, index)
            }
        }
        return line
    }
}
