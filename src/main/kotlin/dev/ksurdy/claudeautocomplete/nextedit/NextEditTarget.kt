package dev.ksurdy.claudeautocomplete.nextedit

data class NextEdit(
    val startLine: Int,
    val endLine: Int,
    val startOffset: Int,
    val endOffset: Int,
    val oldText: String,
    val newText: String,
)

object NextEditTarget {
    const val MAX_LINES = 20

    fun choose(parsed: ParsedEdit?, text: CharSequence, caretLine: Int, maxLines: Int = MAX_LINES): NextEdit? {
        if (parsed == null) return null
        val content = text.toString()
        val lineStarts = lineStarts(content)
        if (parsed.startLine < 1 || parsed.endLine > lineStarts.size) return null
        if (parsed.endLine - parsed.startLine + 1 > maxLines) return null
        if (caretLine in parsed.startLine..parsed.endLine) return null
        val lineStart = lineStarts[parsed.startLine - 1]
        val lineEnd = if (parsed.endLine < lineStarts.size) lineStarts[parsed.endLine] - 1 else content.length
        val oldText = content.substring(lineStart, lineEnd)
        if (parsed.replacement == oldText) return null
        val (start, end) = if (parsed.replacement.isEmpty()) withNewline(content, lineStart, lineEnd) else lineStart to lineEnd
        return NextEdit(parsed.startLine, parsed.endLine, start, end, content.substring(start, end), parsed.replacement)
    }

    private fun withNewline(content: String, start: Int, end: Int): Pair<Int, Int> = when {
        end < content.length -> start to end + 1
        start > 0 -> start - 1 to end
        else -> start to end
    }

    private fun lineStarts(content: String): List<Int> {
        val starts = arrayListOf(0)
        content.forEachIndexed { index, ch -> if (ch == '\n') starts += index + 1 }
        return starts
    }
}
