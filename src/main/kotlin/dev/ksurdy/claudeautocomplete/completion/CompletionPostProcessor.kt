package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

object CompletionPostProcessor {
    private val OPENING_FENCE = Regex("""^\s*```[\w+#.-]*[ \t]*\r?\n""")
    private val CLOSING_FENCE = Regex("""\r?\n?[ \t]*```\s*$""")
    private const val MIN_PARTIAL_ECHO_CHARS = 2
    private const val OPENERS = "([{"
    private const val CLOSERS = ")]}"
    private const val CLOSING_PUNCTUATION = "\"';,`"

    fun process(raw: String, context: CompletionContext): String {
        var text = stripFences(raw.replace("\r\n", "\n"))
        text = trimEchoedHead(text, context.prefix)
        text = limitLines(text, context)
        text = trimOverlappingTail(text, context.suffix)
        return text.trimEnd()
    }

    private fun stripFences(text: String): String {
        if (!text.trimStart().startsWith("```")) return text
        val withoutOpening = OPENING_FENCE.replaceFirst(text, "")
        return CLOSING_FENCE.replaceFirst(withoutOpening, "")
    }

    private fun trimEchoedHead(text: String, prefix: String): String {
        val linePrefix = prefix.substringAfterLast('\n')
        if (linePrefix.isEmpty()) return text
        if (linePrefix.isBlank()) return text.removePrefix(linePrefix)
        val trimmedText = text.trimStart()
        val trimmedLine = linePrefix.trimStart()
        if (trimmedText.startsWith(trimmedLine)) return trimmedText.removePrefix(trimmedLine)
        val overlap = (minOf(trimmedLine.length, trimmedText.length) downTo MIN_PARTIAL_ECHO_CHARS).firstOrNull { length ->
            val candidate = trimmedText.take(length)
            candidate.count { !it.isWhitespace() } >= MIN_PARTIAL_ECHO_CHARS && trimmedLine.endsWith(candidate)
        }
        return if (overlap == null) text else trimmedText.drop(overlap)
    }

    private fun limitLines(text: String, context: CompletionContext): String {
        val blankCaretLine = context.prefix.substringAfterLast('\n').isBlank()
        if (!context.multiline) return text.trimStart('\n').lineSequence().firstOrNull().orEmpty()
        val withoutLeadingBreaks = if (blankCaretLine) text.trimStart('\n') else keepAtMostOneLeadingBreak(text)
        return withoutLeadingBreaks.trimEnd().lines().take(context.maxLines.coerceAtLeast(1)).joinToString("\n")
    }

    private fun keepAtMostOneLeadingBreak(text: String): String {
        val stripped = text.trimStart('\n')
        return if (stripped.length < text.length) "\n$stripped" else text
    }

    private fun trimOverlappingTail(text: String, suffix: String): String {
        val tail = suffix.trimStart()
        val trimmed = text.trimEnd()
        val unmatched = unmatchedClosers(trimmed)
        for (length in minOf(tail.length, trimmed.length) downTo 1) {
            val candidate = tail.take(length)
            if (candidate.isNotBlank() && trimmed.endsWith(candidate) && isTrimmable(candidate, unmatched)) {
                return trimmed.dropLast(length)
            }
        }
        return text
    }

    private fun isTrimmable(candidate: String, unmatched: Int): Boolean {
        val closers = candidate.count { it in CLOSERS }
        if (closers > 0) return closers <= unmatched
        return candidate.all { it in CLOSING_PUNCTUATION || it.isWhitespace() }
    }

    private fun unmatchedClosers(text: String): Int {
        var depth = 0
        var unmatched = 0
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
                in CLOSERS -> if (depth > 0) depth-- else unmatched++
            }
        }
        return unmatched
    }
}
