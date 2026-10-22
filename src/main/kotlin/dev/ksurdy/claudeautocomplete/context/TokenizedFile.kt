package dev.ksurdy.claudeautocomplete.context

class TokenizedFile private constructor(
    private val text: String,
    val tokens: IntArray,
    val tokenCount: Int,
    val lineTokenStart: IntArray,
    private val lineStart: IntArray,
    private val lineEnd: IntArray,
    val isQueryToken: BooleanArray,
) {
    val lineCount: Int get() = lineStart.size
    val vocabularySize: Int get() = isQueryToken.size

    fun slice(startLine: Int, endLine: Int): String = text.substring(lineStart[startLine], lineEnd[endLine - 1])

    fun length(startLine: Int, endLine: Int): Int = lineEnd[endLine - 1] - lineStart[startLine]

    companion object {
        const val MAX_LINES = 5000
        const val MAX_LINE_CHARS = 400

        fun of(text: String, stopWords: Set<String>, queryTokens: Set<String>): TokenizedFile {
            val ids = HashMap<String, Int>()
            val queryFlags = ArrayList<Boolean>()
            var tokens = IntArray(1024)
            var tokenCount = 0
            val lineTokenStart = ArrayList<Int>()
            val lineStart = ArrayList<Int>()
            val lineEnd = ArrayList<Int>()
            var position = 0
            while (position < text.length && lineStart.size < MAX_LINES) {
                val newline = text.indexOf('\n', position)
                val end = if (newline < 0) text.length else newline
                lineStart += position
                lineEnd += if (end > position && text[end - 1] == '\r') end - 1 else end
                lineTokenStart += tokenCount
                IdentifierTokenizer.scan(text, position, minOf(end, position + MAX_LINE_CHARS)) { from, to ->
                    val token = text.substring(from, to)
                    if (token !in stopWords) {
                        val id = ids.getOrPut(token) {
                            queryFlags += token in queryTokens
                            ids.size
                        }
                        if (tokenCount == tokens.size) tokens = tokens.copyOf(tokens.size * 2)
                        tokens[tokenCount++] = id
                    }
                }
                position = end + 1
            }
            lineTokenStart += tokenCount
            return TokenizedFile(
                text,
                tokens,
                tokenCount,
                lineTokenStart.toIntArray(),
                lineStart.toIntArray(),
                lineEnd.toIntArray(),
                queryFlags.toBooleanArray(),
            )
        }
    }
}
