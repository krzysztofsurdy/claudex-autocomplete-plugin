package dev.ksurdy.claudeautocomplete.ui

enum class Outcome { Received, Emptied, Empty, Cancelled, Failed, TimedOut }

private val CHAT_OPENER = Regex("""^(The|Here|Sure|Wait|I|Let me|I'll)\b""")

fun proseProblem(text: String): String? = when {
    text.contains("cursor", ignoreCase = true) -> "inserted text mentions the cursor"
    CHAT_OPENER.containsMatchIn(text.trimStart()) -> "inserted text starts like a chat reply"
    else -> null
}

fun outcomeOf(lastFinished: String?, newLogLines: List<String>): Outcome = when {
    lastFinished == null -> Outcome.TimedOut
    COMPLETION_FAILED in lastFinished -> Outcome.Failed
    COMPLETION_CANCELLED in lastFinished -> Outcome.Cancelled
    COMPLETION_EMPTY in lastFinished -> Outcome.Empty
    newLogLines.any { COMPLETION_EMPTIED in it } -> Outcome.Emptied
    else -> Outcome.Received
}

class Observation(
    val outcome: Outcome,
    val before: String,
    val after: String,
    val caretOffset: Int,
    val newLogLines: List<String>,
) {
    val inserted: String get() = after.substring(caretOffset, caretOffset + after.length - before.length)

    fun caretLineAfter(): String {
        val start = after.lastIndexOf('\n', caretOffset - 1) + 1
        val end = after.indexOf('\n', caretOffset).takeIf { it >= 0 } ?: after.length
        return after.substring(start, end)
    }

    fun report(shown: String = inserted): String =
        "outcome=$outcome inserted=<<\n${shown.replace(" ", "·")}>>\nlog:\n${newLogLines.joinToString("\n").ifEmpty { "(no plugin log lines)" }}"
}
