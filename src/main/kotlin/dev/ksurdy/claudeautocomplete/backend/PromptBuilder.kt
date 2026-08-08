package dev.ksurdy.claudeautocomplete.backend

object PromptBuilder {
    private val BASE_SYSTEM_PROMPT = """
        You are an inline code completion engine (fill-in-the-middle) embedded in a code editor.
        The user message contains a file with a <CURSOR/> marker, optionally other open files for context, and a <mode>.
        Output ONLY the raw text to insert at <CURSOR/>.
        Rules:
        - No markdown, no code fences, no explanations, no surrounding quotes.
        - Never repeat any text that appears before the cursor or after the cursor.
        - Keep indentation and code style consistent with the surrounding file.
        - In single-line mode output exactly one line, without a trailing newline.
        - In multi-line mode output at most the stated number of lines.
        - If nothing sensible can be inserted, output nothing at all.
    """.trimIndent()

    fun systemPrompt(customInstructions: String): String {
        val custom = customInstructions.trim()
        return if (custom.isEmpty()) BASE_SYSTEM_PROMPT else "$BASE_SYSTEM_PROMPT\n\nAdditional instructions:\n$custom"
    }

    fun userMessage(context: CompletionContext): String = buildString {
        append("<file path=\"").append(context.filePath).append("\" language=\"").append(context.languageId).append("\">\n")
        append(context.prefix).append("<CURSOR/>").append(context.suffix).append("\n</file>\n")
        if (context.openFiles.isNotEmpty()) {
            append("<open_files>\n")
            for (file in context.openFiles) {
                append("<file path=\"").append(file.path).append("\" language=\"").append(file.languageId).append("\">")
                append(file.content).append("</file>\n")
            }
            append("</open_files>\n")
        }
        if (context.multiline) append("<mode>multi-line (max ${context.maxLines} lines)</mode>")
        else append("<mode>single-line</mode>")
    }
}
