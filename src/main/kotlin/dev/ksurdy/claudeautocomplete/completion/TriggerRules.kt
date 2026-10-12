package dev.ksurdy.claudeautocomplete.completion

object TriggerRules {
    private const val CLOSING_CHARS = ")]}>\"';,`"
    private val BLOCK_OPENERS = listOf("{", "[", ":")

    fun shouldTrigger(prefix: String, suffix: String): Boolean {
        val before = prefix.lastOrNull() ?: return true
        val after = suffix.firstOrNull() ?: return true
        return !(isWordChar(before) && isWordChar(after))
    }

    fun isMultiline(prefix: String, suffix: String, mode: String): Boolean = when (mode.lowercase()) {
        "always" -> true
        "never" -> false
        else -> isBlankCaretLine(prefix) || (restOfLineIsClosers(suffix) && endsWithBlockOpener(prefix))
    }

    fun isEmptyBody(prefix: String, suffix: String): Boolean =
        prefix.trimEnd().endsWith("{") && suffix.trimStart().startsWith("}")

    private fun restOfLineIsClosers(suffix: String): Boolean =
        suffix.substringBefore('\n').all { it.isWhitespace() || it in CLOSING_CHARS }

    private fun isWordChar(char: Char): Boolean = char.isLetterOrDigit() || char == '_' || char == '$'

    private fun isBlankCaretLine(prefix: String): Boolean = prefix.substringAfterLast('\n').isBlank()

    private fun endsWithBlockOpener(prefix: String): Boolean {
        val trimmed = prefix.trimEnd(' ', '\t')
        if (trimmed.endsWith("::")) return false
        return BLOCK_OPENERS.any { trimmed.endsWith(it) }
    }
}
