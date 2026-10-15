package dev.ksurdy.claudeautocomplete.completion

internal object RepetitionGuard {
    private class Token(val text: String, val start: Int) {
        val whitespace: Boolean get() = text[0].isWhitespace()
        val symbol: Boolean get() = !whitespace && !text[0].isLetterOrDigit() && text[0] != '_' && text[0] != '$'
    }

    private class Setting(val maxPattern: Int, val window: Int)

    private val TOKEN = Regex("""\s+|[\p{L}_$][\p{L}\p{N}_$]*|\p{N}+(?:\.\p{N}+)?|\S""")
    private val SETTINGS = listOf(Setting(1, 24), Setting(4, 40), Setting(12, 72), Setting(40, 160))
    private const val SYMBOL_WINDOW = 120
    private const val MIN_REPEATS = 3
    private const val HOLD_BACK_REPEATS = 2
    private const val HOLD_BACK_DIVISOR = 6

    fun cut(text: String): String? = cutAt(text, loopStart(text, strict = true))

    fun holdBack(text: String): String = cutAt(text, loopStart(text, strict = false)) ?: text

    private fun cutAt(text: String, start: Int?): String? {
        if (start == null) return null
        val before = text.substring(0, start)
        val startsLine = '\n' in text.substring(start).takeWhile { it.isWhitespace() }
        return if (startsLine) before.trimEnd() else before.substring(0, before.lastIndexOf('\n') + 1).trimEnd()
    }

    private fun loopStart(text: String, strict: Boolean): Int? {
        if (text.isBlank()) return null
        val all = TOKEN.findAll(text).map { Token(it.value, it.range.first) }.toList()
        val words = all.filterNot { it.whitespace }
        return listOf(all, words).mapNotNull { tokens -> earliestStart(tokens, strict) }.minOrNull()
    }

    private fun earliestStart(tokens: List<Token>, strict: Boolean): Int? =
        SETTINGS.mapNotNull { setting -> periodicStart(tokens, setting, strict) }.minOrNull()

    private fun periodicStart(tokens: List<Token>, setting: Setting, strict: Boolean): Int? {
        val size = tokens.size
        val minRepeats = if (strict) MIN_REPEATS else HOLD_BACK_REPEATS
        var best: Int? = null
        for (pattern in 1..minOf(setting.maxPattern, size / minRepeats)) {
            var matched = 0
            while (size - 1 - matched - pattern >= 0 && repeats(tokens, size - 1 - matched, pattern)) matched++
            val length = (matched + pattern) / pattern * pattern
            val symbolsOnly = (size - pattern until size).all { tokens[it].symbol || tokens[it].whitespace }
            val needed = scaled(if (symbolsOnly) SYMBOL_WINDOW else setting.window, strict)
            if (length >= needed && length >= pattern * minRepeats) best = minOf(best ?: Int.MAX_VALUE, size - length)
        }
        return best?.let { tokens[it].start }
    }

    private fun repeats(tokens: List<Token>, index: Int, pattern: Int): Boolean {
        val token = tokens[index]
        val earlier = tokens[index - pattern]
        val truncatedLast = index == tokens.lastIndex && !token.whitespace
        return token.text == earlier.text || (truncatedLast && earlier.text.startsWith(token.text))
    }

    private fun scaled(window: Int, strict: Boolean): Int = if (strict) window else window / HOLD_BACK_DIVISOR
}
