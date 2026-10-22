package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

enum class ReplySource { Tagged, Bare, ProseRejected, Pending }

data class Reply(val content: String, val source: ReplySource, val closed: Boolean = false)

object CompletionReply {
    const val OPEN_TAG = "<completion>"
    const val CLOSE_TAG = "</completion>"
    private const val MIN_FINAL_PARTIAL_CLOSE = 2

    fun parse(raw: String, context: CompletionContext, final: Boolean): Reply {
        val open = raw.indexOf(OPEN_TAG)
        if (open >= 0) return tagged(raw.substring(open + OPEN_TAG.length), final)
        if (!final && raw.isNotBlank() && OPEN_TAG.startsWith(raw.trim())) return Reply("", ReplySource.Pending)
        return bare(raw.replace("\r\n", "\n"), context)
    }

    private fun tagged(afterOpen: String, final: Boolean): Reply {
        val close = afterOpen.indexOf(CLOSE_TAG)
        if (close >= 0) return Reply(afterOpen.substring(0, close), ReplySource.Tagged, closed = true)
        return Reply(withoutPartialCloseTag(afterOpen, final), ReplySource.Tagged)
    }

    private fun withoutPartialCloseTag(text: String, final: Boolean): String {
        val shortest = if (final) MIN_FINAL_PARTIAL_CLOSE else 1
        val held = (CLOSE_TAG.length - 1 downTo shortest).firstOrNull { text.endsWith(CLOSE_TAG.take(it)) } ?: return text
        return text.dropLast(held)
    }

    private fun bare(raw: String, context: CompletionContext): Reply {
        if (ProseGuard.rejects(raw, context)) return Reply("", ReplySource.ProseRejected)
        val content = ProseGuard.withoutTrailingProse(raw, context)
        return Reply(content, ReplySource.Bare, closed = content.length < raw.length)
    }
}
