package dev.ksurdy.claudeautocomplete.completion

object TriggerRules {
    private const val CLOSING_CHARS = ")]}>\"';,`"
    private val BLOCK_OPENERS = listOf("{", "(", "[", ":", "=>", "->")
    private val FUNCTION_SIGNATURE = Regex("""\b(function|fn|def|fun)\b.*\)\s*(:\s*\??[\w\\|]+)?$""")

    fun shouldTrigger(prefix: String, suffix: String): Boolean =
        suffix.substringBefore('\n').all { it.isWhitespace() || it in CLOSING_CHARS }

    fun isMultiline(prefix: String, suffix: String, mode: String): Boolean = when (mode.lowercase()) {
        "always" -> true
        "never" -> false
        else -> isBlankCaretLine(prefix) || endsWithBlockOpener(prefix)
    }

    private fun isBlankCaretLine(prefix: String): Boolean = prefix.substringAfterLast('\n').isBlank()

    private fun endsWithBlockOpener(prefix: String): Boolean {
        val trimmed = prefix.trimEnd()
        if (BLOCK_OPENERS.any { trimmed.endsWith(it) }) return true
        return FUNCTION_SIGNATURE.containsMatchIn(trimmed.substringAfterLast('\n'))
    }
}
