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
        text = alignToCaretIndent(text, context)
        val restOfLine = context.suffix.substringBefore('\n')
        text = trimEchoedRestOfLine(text, restOfLine)
        text = trimOverlappingTail(text, context.suffix)
        return if (restOfLine.isBlank()) text.trimEnd() else text.trimEnd('\n')
    }

    private fun trimEchoedRestOfLine(text: String, restOfLine: String): String {
        val rest = restOfLine.trim()
        if (rest.all { it in CLOSERS || it in CLOSING_PUNCTUATION || it.isWhitespace() }) return text
        val trimmed = text.trimEnd()
        return if (trimmed.length > rest.length && trimmed.endsWith(rest)) trimmed.dropLast(rest.length) else text
    }

    private fun stripFences(text: String): String {
        if (!text.trimStart().startsWith("```")) return text
        val withoutOpening = OPENING_FENCE.replaceFirst(text, "")
        return CLOSING_FENCE.replaceFirst(withoutOpening, "")
    }

    private fun trimEchoedHead(text: String, prefix: String): String {
        val linePrefix = prefix.substringAfterLast('\n')
        if (linePrefix.isEmpty()) return text
        if (linePrefix.isBlank()) return text.trimStart('\n').trimStart(' ', '\t')
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
        val lines = withoutLeadingBreaks.trimEnd().lines()
        val maxLines = context.maxLines.coerceAtLeast(1)
        if (lines.size <= maxLines) return lines.joinToString("\n")
        return lines.take(balancedLineCount(lines, maxLines) ?: maxLines).joinToString("\n")
    }

    private fun balancedLineCount(lines: List<String>, maxLines: Int): Int? {
        var depth = 0
        var balanced: Int? = null
        for (index in 0 until maxLines) {
            depth += bracketBalance(lines[index])
            if (depth <= 0) balanced = index + 1
        }
        return balanced
    }

    private fun bracketBalance(line: String): Int {
        val scan = scanBrackets(line.substringBeforeLineComment())
        return scan.depth - scan.unmatched
    }

    private fun String.substringBeforeLineComment(): String {
        var quote: Char? = null
        var escaped = false
        for ((index, char) in withIndex()) {
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
                char == '/' && getOrNull(index + 1) == '/' -> return substring(0, index)
            }
        }
        return this
    }

    private fun alignToCaretIndent(text: String, context: CompletionContext): String {
        val caretIndent = context.prefix.substringAfterLast('\n')
        if (!context.multiline || caretIndent.isEmpty() || caretIndent.isNotBlank()) return text
        val lines = text.lines()
        val continuation = lines.drop(1).filter { it.isNotBlank() }
        if (continuation.isEmpty()) return text
        val minIndent = continuation.minOf { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
        if (minIndent >= caretIndent.length) return text
        val shift = caretIndent.take(caretIndent.length - minIndent)
        return (listOf(lines.first()) + lines.drop(1).map { if (it.isBlank()) it else shift + it }).joinToString("\n")
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

    private class BracketScan(val depth: Int, val unmatched: Int)

    private fun unmatchedClosers(text: String): Int = scanBrackets(text).unmatched

    private fun scanBrackets(text: String): BracketScan {
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
        return BracketScan(depth, unmatched)
    }
}
