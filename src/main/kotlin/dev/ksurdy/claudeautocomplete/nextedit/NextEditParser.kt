package dev.ksurdy.claudeautocomplete.nextedit

data class ParsedEdit(val startLine: Int, val endLine: Int, val replacement: String)

object NextEditParser {
    private val OPEN_TAG = Regex("<edit\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val CLOSE_TAG = Regex("</edit\\s*>", RegexOption.IGNORE_CASE)
    private val LINE_PREFIX = Regex("^(\\d+)\\|")

    fun parse(raw: String): ParsedEdit? {
        val open = OPEN_TAG.find(raw) ?: return null
        val close = CLOSE_TAG.find(raw, open.range.last + 1) ?: return null
        val start = attribute(open.groupValues[1], "start_line") ?: return null
        val end = attribute(open.groupValues[1], "end_line") ?: start
        if (start < 1 || end < start) return null
        val body = raw.substring(open.range.last + 1, close.range.first)
        return ParsedEdit(start, end, cleanReplacement(body, start))
    }

    private fun attribute(attributes: String, name: String): Int? =
        Regex("\\b$name\\s*=\\s*[\"']?\\s*(\\d+)\\s*[\"']?").find(attributes)?.groupValues?.get(1)?.toIntOrNull()

    private fun cleanReplacement(body: String, startLine: Int): String {
        var text = body.removePrefix("\r\n").removePrefix("\n").removeSuffix("\n").removeSuffix("\r")
        text = stripFence(text)
        return stripLineNumbers(text, startLine)
    }

    private fun stripFence(text: String): String {
        val lines = text.split('\n')
        if (lines.size < 2 || !lines.first().trim().startsWith("```") || lines.last().trim() != "```") return text
        return lines.subList(1, lines.size - 1).joinToString("\n")
    }

    private fun stripLineNumbers(text: String, startLine: Int): String {
        if (text.isEmpty()) return text
        val lines = text.split('\n')
        val numbered = lines.mapIndexed { index, line -> LINE_PREFIX.find(line)?.takeIf { it.groupValues[1].toIntOrNull() == startLine + index } }
        if (numbered.any { it == null }) return text
        return lines.mapIndexed { index, line -> line.substring(numbered[index]!!.value.length) }.joinToString("\n")
    }
}
