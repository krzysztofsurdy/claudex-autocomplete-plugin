package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

object CompletionPostProcessor {
    private val OPENING_FENCE = Regex("""^\s*```[\w+#.-]*[ \t]*\r?\n""")
    private val CLOSING_FENCE = Regex("""\r?\n?[ \t]*```\s*$""")
    private const val MIN_PARTIAL_ECHO_CHARS = 2
    private const val CLOSERS = Brackets.CLOSERS
    private const val CLOSING_PUNCTUATION = "\"';,`"

    class Processed(val text: String, val source: ReplySource, val extractedLength: Int, val cuts: List<GuardCut> = emptyList())

    fun process(raw: String, context: CompletionContext): String = interpret(raw, context).text

    fun interpret(raw: String, context: CompletionContext): Processed {
        val reply = CompletionReply.parse(raw, context, final = true)
        if (reply.source == ReplySource.ProseRejected) return Processed("", reply.source, 0)
        val guarded = formatGuarded(reply.content, context)
        return Processed(guarded.text, reply.source, reply.content.length, guarded.cuts)
    }

    fun format(content: String, context: CompletionContext): String = formatGuarded(content, context).text

    private fun formatGuarded(content: String, context: CompletionContext): QualityGuards.Result {
        var text = stripFences(content.replace("\r\n", "\n"))
        text = trimEchoedHead(text, context.prefix)
        text = limitLines(text, context)
        text = alignToCaretIndent(text, context)
        val guarded = QualityGuards.apply(text, context)
        val restOfLine = context.suffix.substringBefore('\n')
        text = trimEchoedRestOfLine(guarded.text, restOfLine)
        text = trimOverlappingTail(text, context.suffix)
        return QualityGuards.Result(if (restOfLine.isBlank()) text.trimEnd() else text.trimEnd('\n'), guarded.cuts)
    }

    private fun trimEchoedRestOfLine(text: String, restOfLine: String): String {
        val rest = restOfLine.trim()
        if (rest.all { it in CLOSERS || it in CLOSING_PUNCTUATION || it.isWhitespace() }) return text
        val trimmed = text.trimEnd()
        return if (trimmed.length > rest.length && trimmed.endsWith(rest)) trimmed.dropLast(rest.length) else text
    }

    fun balancedPrefix(text: String): String {
        val lines = text.lines()
        var depth = 0
        var balanced = 0
        for ((index, line) in lines.withIndex()) {
            depth += Brackets.balance(line)
            if (depth < 0) break
            if (depth == 0) balanced = index + 1
        }
        return lines.take(balanced).joinToString("\n")
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
        val depths = runningDepths(lines)
        val blockEnd = firstBlockEnd(depths)?.takeUnless { suffixClosesBlock(context.suffix) }
        if (context.declaration && blockEnd != null) return lines.take(blockEnd + 1).joinToString("\n")
        val balanced = (0 until maxLines).lastOrNull { depths[it] == 0 }
        val count = when {
            balanced != null -> balanced + 1
            blockEnd != null -> blockEnd + 1
            else -> maxLines
        }
        return lines.take(count).joinToString("\n")
    }

    private fun firstBlockEnd(depths: List<Int>): Int? {
        val opened = depths.indexOfFirst { it > 0 }
        if (opened < 0) return null
        return (opened + 1 until depths.size).firstOrNull { depths[it] == 0 }
    }

    private fun suffixClosesBlock(suffix: String): Boolean = suffix.trimStart().firstOrNull()?.let { it in CLOSERS } == true

    private fun runningDepths(lines: List<String>): List<Int> {
        var depth = 0
        return lines.map { line ->
            val scan = Brackets.scan(Brackets.substringBeforeLineComment(line))
            depth = (depth - scan.unmatched).coerceAtLeast(0) + scan.depth
            depth
        }
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
        val unmatched = Brackets.scan(trimmed).unmatchedByKind
        for (length in minOf(tail.length, trimmed.length) downTo 1) {
            val candidate = tail.take(length)
            if (candidate.isNotBlank() && trimmed.endsWith(candidate) && isTrimmable(candidate, unmatched)) {
                return trimmed.dropLast(length)
            }
        }
        return text
    }

    private fun isTrimmable(candidate: String, unmatched: Map<Char, Int>): Boolean {
        val closers = candidate.filter { it in CLOSERS }
        if (closers.isEmpty()) return candidate.all { it in CLOSING_PUNCTUATION || it.isWhitespace() }
        return closers.groupingBy { it }.eachCount().all { (kind, count) -> count <= (unmatched[kind] ?: 0) }
    }
}
