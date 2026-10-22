package dev.ksurdy.claudeautocomplete.context

object IdentifierTokenizer {
    const val MIN_LENGTH = 2

    fun tokens(text: CharSequence, stopWords: Set<String>): Set<String> {
        val result = LinkedHashSet<String>()
        scan(text, 0, text.length) { start, end ->
            val token = text.substring(start, end)
            if (token !in stopWords) result += token
        }
        return result
    }

    inline fun scan(text: CharSequence, from: Int, to: Int, onToken: (Int, Int) -> Unit) {
        var index = from
        while (index < to) {
            val char = text[index]
            if (char.isLetter() || char == '_') {
                val start = index
                index = wordEnd(text, index, to)
                if (index - start >= MIN_LENGTH) onToken(start, index)
            } else if (char.isDigit()) {
                index = wordEnd(text, index, to)
            } else {
                index++
            }
        }
    }

    fun wordEnd(text: CharSequence, from: Int, to: Int): Int {
        var index = from
        while (index < to && (text[index].isLetterOrDigit() || text[index] == '_')) index++
        return index
    }
}
