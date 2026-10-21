package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

object ProseGuard {
    private val PROSE_LANGUAGES = setOf("markdown", "text", "plain_text", "plaintext", "asciidoc", "restructuredtext")
    private val COMMENT_STARTS = listOf("//", "#", "/*", "*", "--", "<!--", ";", "%", "\"\"\"", "'''")
    private val CHAT_OPENER = Regex(
        """^(The cursor|The caret|Wait|Hmm+|Actually|Okay|OK|Sure|Certainly|Of course|Let me|Let's|I think|I'll|I'm|I need|I should|I can|I will|I see|I notice)\b""",
    )
    private val SOFT_OPENER = Regex(
        """^(Here|Note|This|Looking|Based on|Since|It looks|It seems|There|You|Your|Unfortunately|Sorry|Apologies|I)\b""",
    )
    private val BACKTICK_SPAN = Regex("`[^`]*`")
    private val CURSOR_TAG = Regex("<CURSOR/>", RegexOption.IGNORE_CASE)
    private val CURSOR_WORD = Regex("""\b(cursor|caret)\b""", RegexOption.IGNORE_CASE)
    private val CODE_TOKEN = Regex("""[{}\[\];=<>${'$'}@\\|&*+~^]|\w\(|->|::|\w_\w""")
    private val PROSE_WORD = Regex("""^[A-Za-z0-9][\w'’-]*[,.:!?]*$""")
    private val SENTENCE_END = Regex("""[.!?:,]$""")
    private val FINAL_SENTENCE_END = Regex("""[.!?:]$""")
    private const val MIN_OPENER_WORDS = 3
    private const val MIN_CURSOR_TAG_WORDS = 3
    private const val MIN_SENTENCE_WORDS = 4

    fun rejects(text: String, context: CompletionContext): Boolean {
        if (isProseLanguage(context)) return false
        val line = firstLine(text.lines()) ?: return false
        return mentionsCursorTag(line) || (!inProseContext(context) && readsLikeProse(line))
    }

    fun withoutTrailingProse(text: String, context: CompletionContext): String {
        if (isProseLanguage(context)) return text
        val lines = text.lines()
        val first = lines.indexOfFirst { isContentLine(it) }
        if (first < 0) return text
        val proseContext = inProseContext(context)
        val cut = (first + 1 until lines.size).firstOrNull { index ->
            val line = lines[index]
            isContentLine(line) && (mentionsCursorTag(line) || (!proseContext && isChatLine(line)))
        } ?: return text
        return lines.take(cut).joinToString("\n")
    }

    private fun isProseLanguage(context: CompletionContext): Boolean = context.languageId.lowercase() in PROSE_LANGUAGES

    private fun firstLine(lines: List<String>): String? = lines.firstOrNull { isContentLine(it) }?.trim()

    private fun isContentLine(line: String): Boolean = line.isNotBlank() && !line.trim().startsWith("```")

    private fun readsLikeProse(line: String): Boolean {
        val trimmed = line.trim()
        if (COMMENT_STARTS.any { trimmed.startsWith(it) }) return false
        return isChatLine(trimmed) || startsLikeSoftOpener(trimmed) || isPlainSentence(trimmed)
    }

    private fun isChatLine(line: String): Boolean {
        val trimmed = line.trim()
        if (COMMENT_STARTS.any { trimmed.startsWith(it) } || hasCodeTokens(trimmed)) return false
        val words = wordCount(trimmed)
        if (CHAT_OPENER.containsMatchIn(trimmed)) return words >= MIN_OPENER_WORDS || SENTENCE_END.containsMatchIn(trimmed)
        return mentionsCursorWord(trimmed, words)
    }

    private fun startsLikeSoftOpener(line: String): Boolean {
        if (!SOFT_OPENER.containsMatchIn(line) || hasCodeTokens(line)) return false
        return wordCount(line) >= MIN_OPENER_WORDS || FINAL_SENTENCE_END.containsMatchIn(line)
    }

    private fun mentionsCursorWord(line: String, words: Int): Boolean =
        words >= MIN_SENTENCE_WORDS && CURSOR_WORD.containsMatchIn(stripSpans(line)) && SENTENCE_END.containsMatchIn(line)

    private fun mentionsCursorTag(line: String): Boolean {
        if (!CURSOR_TAG.containsMatchIn(line)) return false
        val rest = stripSpans(line)
        return wordCount(rest) >= MIN_CURSOR_TAG_WORDS && !hasCodeTokens(rest)
    }

    private fun isPlainSentence(line: String): Boolean {
        if (hasCodeTokens(line) || !FINAL_SENTENCE_END.containsMatchIn(line)) return false
        val words = line.split(Regex("\\s+"))
        return words.size >= MIN_SENTENCE_WORDS && words.all { PROSE_WORD.matches(it) }
    }

    private fun stripSpans(line: String): String = CURSOR_TAG.replace(BACKTICK_SPAN.replace(line, " "), " ")

    private fun hasCodeTokens(line: String): Boolean = CODE_TOKEN.containsMatchIn(stripSpans(line))

    private fun wordCount(line: String): Int = line.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun inProseContext(context: CompletionContext): Boolean {
        val prefix = context.prefix
        val line = prefix.substringAfterLast('\n')
        val trimmed = line.trimStart()
        return COMMENT_STARTS.any { trimmed.startsWith(it) } ||
            line.contains("//") ||
            line.contains(" #") ||
            hasUnclosed(prefix, "/*", "*/") ||
            hasUnclosed(prefix, "<!--", "-->") ||
            prefix.windowed(3).count { it == "\"\"\"" || it == "'''" } % 2 == 1 ||
            listOf('"', '\'', '`').any { quote -> line.count { it == quote } % 2 == 1 }
    }

    private fun hasUnclosed(prefix: String, open: String, close: String): Boolean =
        prefix.lastIndexOf(open) > prefix.lastIndexOf(close)
}
