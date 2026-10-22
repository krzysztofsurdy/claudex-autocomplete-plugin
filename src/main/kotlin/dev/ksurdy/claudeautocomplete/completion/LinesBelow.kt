package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

internal object LinesBelow {
    private const val MAX_LINES = 60

    fun restOfLineIsBlank(context: CompletionContext): Boolean = context.suffix.substringBefore('\n').isBlank()

    fun following(context: CompletionContext): List<String> =
        context.suffix.lineSequence().drop(1).map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES).toList()
}
