package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

internal object NextLineGuard {
    private const val MIN_MEANINGFUL_CHARS = 3

    fun trim(text: String, context: CompletionContext): String? {
        val tails = candidateTails(text, context) ?: return null
        val originalDepth = Brackets.scan(text).depth
        for (tail in tails) {
            if (!meaningful(tail.compared)) continue
            val kept = tail.kept.trimEnd()
            if (Brackets.scan(kept).depth <= originalDepth) return kept
        }
        return null
    }

    fun holdBack(text: String, context: CompletionContext): String {
        val tails = candidateTails(text, context) ?: return text
        val held = tails.firstOrNull { tail -> tail.compared.any { !CloserSnip.isCloserLine(it) } } ?: return text
        return held.kept.trimEnd()
    }

    private class Tail(val kept: String, val compared: List<String>)

    private fun candidateTails(text: String, context: CompletionContext): List<Tail>? {
        if (!LinesBelow.restOfLineIsBlank(context)) return null
        val following = LinesBelow.following(context)
        if (following.isEmpty()) return null
        val lines = text.lines()
        val caretLine = context.prefix.substringAfterLast('\n')
        val compared = lines.mapIndexed { index, line -> (if (index == 0) caretLine + line else line).trim() }
        val nonBlank = lines.indices.filter { lines[it].isNotBlank() }
        return (minOf(nonBlank.size, following.size) downTo 1).mapNotNull { count ->
            val tail = nonBlank.takeLast(count)
            if (tail.map { compared[it] } != following.take(count)) return@mapNotNull null
            Tail(lines.take(tail.first()).joinToString("\n"), tail.map { compared[it] })
        }
    }

    private fun meaningful(compared: List<String>): Boolean =
        compared.any { !CloserSnip.isCloserLine(it) } && compared.sumOf { line -> line.count { it.isLetterOrDigit() } } >= MIN_MEANINGFUL_CHARS
}
