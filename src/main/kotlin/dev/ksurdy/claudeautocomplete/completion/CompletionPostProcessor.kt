package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

object CompletionPostProcessor {
    private val OPENING_FENCE = Regex("""^\s*```[\w+#.-]*[ \t]*\r?\n""")
    private val CLOSING_FENCE = Regex("""\r?\n?[ \t]*```\s*$""")

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
        return if (linePrefix.isNotEmpty() && text.startsWith(linePrefix)) text.removePrefix(linePrefix) else text
    }

    private fun limitLines(text: String, context: CompletionContext): String {
        if (!context.multiline) return text.trimStart('\n').lineSequence().firstOrNull().orEmpty()
        val lines = text.trimEnd().lines()
        return lines.take(context.maxLines.coerceAtLeast(1)).joinToString("\n")
    }

    private fun trimOverlappingTail(text: String, suffix: String): String {
        val tail = suffix.trimStart()
        val trimmed = text.trimEnd()
        for (length in minOf(tail.length, trimmed.length) downTo 1) {
            val candidate = tail.take(length)
            if (candidate.isNotBlank() && trimmed.endsWith(candidate)) return trimmed.dropLast(length)
        }
        return text
    }
}
