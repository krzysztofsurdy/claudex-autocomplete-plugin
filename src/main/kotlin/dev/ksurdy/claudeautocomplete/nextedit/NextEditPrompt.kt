package dev.ksurdy.claudeautocomplete.nextedit

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

object NextEditPrompt {
    fun userMessage(context: CompletionContext, input: NextEditInput): String = buildString {
        append("<recent_edits>\n")
        for (hunk in input.edits) {
            append("<edit line=\"").append(hunk.startLine).append("\">\n")
            append("<before>\n").append(escape(hunk.before)).append("\n</before>\n")
            append("<after>\n").append(escape(hunk.after)).append("\n</after>\n")
            append("</edit>\n")
        }
        append("</recent_edits>\n")
        append("<numbered_file path=\"").append(context.filePath).append("\" language=\"").append(context.languageId).append('"')
        if (context.indent.isNotEmpty()) append(" indent=\"").append(context.indent).append('"')
        append(">\n")
        input.windowText.split('\n').forEachIndexed { index, line ->
            append(input.firstLine + index).append("| ").append(escape(line)).append('\n')
        }
        append("</numbered_file>\n")
        append("<caret line=\"").append(input.caretLine).append("\"/>\n")
        append("<mode>next-edit</mode>")
    }

    private fun escape(content: String): String =
        content.replace("</numbered_file>", "<\\/numbered_file>")
            .replace("</recent_edits>", "<\\/recent_edits>")
            .replace("</before>", "<\\/before>")
            .replace("</after>", "<\\/after>")
}
