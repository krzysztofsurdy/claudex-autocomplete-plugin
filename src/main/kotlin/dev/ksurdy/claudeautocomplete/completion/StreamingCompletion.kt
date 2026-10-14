package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

class StreamingCompletion(private val context: CompletionContext) {
    var text: String = ""
        private set

    fun onPartial(raw: String): String {
        if (!context.multiline) return ""
        val completeLines = raw.replace("\r\n", "\n").substringBeforeLast('\n', "")
        if (completeLines.isBlank()) return ""
        return advance(CompletionPostProcessor.balancedPrefix(CompletionPostProcessor.process(completeLines, context)))
    }

    fun onFinal(raw: String): String = advance(CompletionPostProcessor.process(raw, context))

    private fun advance(next: String): String {
        if (next.length <= text.length || !next.startsWith(text)) return ""
        val delta = next.substring(text.length)
        text = next
        return delta
    }
}
