package dev.ksurdy.claudeautocomplete.completion

object TriggerRules {
    private const val PAIRED_CLOSER = ")"
    private const val CLOSING_CHARS = ")]}>\"';,`"
    private val BLOCK_OPENERS = listOf("{", "[", ":", "(")
    private val OPEN_DECLARATION = Regex(
        """^[^=(.;]*\b(function|fun|def|func|fn)\b[^=;]*\(\s*$""" + "|" +
            """^\s*(?:(?:public|private|protected|internal|static|abstract|final|override|open|suspend|async|export|default|virtual|inline|synchronized)\s+)+[^=;(]*\(\s*$""",
    )

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

    fun isOpenDeclaration(prefix: String, suffix: String): Boolean =
        (suffix.substringBefore('\n').isBlank() || pairedCloser(prefix, suffix).isNotEmpty()) &&
            OPEN_DECLARATION.containsMatchIn(prefix.substringAfterLast('\n'))

    fun pairedCloser(prefix: String, suffix: String): String {
        val rest = suffix.substringBefore('\n')
        val paired = rest.startsWith(PAIRED_CLOSER) && rest.drop(PAIRED_CLOSER.length).isBlank()
        return if (paired && OPEN_DECLARATION.containsMatchIn(prefix.substringAfterLast('\n'))) PAIRED_CLOSER else ""
    }

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
