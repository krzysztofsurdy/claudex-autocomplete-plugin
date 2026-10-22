package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

class StreamingCompletion(private val context: CompletionContext) {
    var text: String = ""
        private set

    var processed: CompletionPostProcessor.Processed = CompletionPostProcessor.Processed("", ReplySource.Pending, 0)
        private set

    fun onPartial(raw: String): String {
        if (!context.multiline) return ""
        val reply = CompletionReply.parse(raw, context, final = false)
        if (reply.source == ReplySource.ProseRejected || reply.source == ReplySource.Pending) return ""
        val completeLines = if (reply.closed) reply.content else reply.content.replace("\r\n", "\n").substringBeforeLast('\n', "")
        if (completeLines.isBlank()) return ""
        val formatted = CompletionPostProcessor.balancedPrefix(CompletionPostProcessor.format(completeLines, context))
        return advance(QualityGuards.holdBack(formatted, context))
    }

    fun onFinal(raw: String): String {
        processed = CompletionPostProcessor.interpret(raw, context)
        return advance(processed.text)
    }

    private fun advance(next: String): String {
        if (next.length <= text.length || !next.startsWith(text)) return ""
        val delta = next.substring(text.length)
        text = next
        return delta
    }
}
