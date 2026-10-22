package dev.ksurdy.claudeautocomplete.nextedit

object NextEditLog {
    private val NONE_REPLY = Regex("<none\\s*/?>", RegexOption.IGNORE_CASE)

    fun requested(languageId: String, edits: Int) = "Requesting next edit: language=$languageId edits=$edits"

    fun received(ms: Long, parsed: ParsedEdit?) =
        "Next edit received: ms=$ms target=${parsed?.let { range(it.startLine, it.endLine) } ?: "none"}"

    fun empty(ms: Long) = "Next edit empty: ms=$ms"

    fun failed(kind: String, ms: Long) = "Next edit failed: kind=$kind ms=$ms"

    fun cancelled(ms: Long) = "Next edit cancelled: ms=$ms"

    fun dropped(reason: String) = "Next edit dropped: reason=$reason"

    fun reason(reason: DropReason, raw: String): String = when (reason) {
        DropReason.Unparsable -> if (NONE_REPLY.containsMatchIn(raw)) "model replied none" else "unparsable"
        DropReason.OutsideFile -> "outside file"
        DropReason.TooLong -> "too long"
        DropReason.CaretLine -> "caret line"
        DropReason.NoOp -> "no-op"
    }

    fun shown(edit: NextEdit) = "Next edit shown: lines=${range(edit.startLine, edit.endLine)} replacementLines=${replacementLines(edit)}"

    fun jumped(edit: NextEdit) = "Next edit jumped: line=${edit.startLine}"

    fun applied(edit: NextEdit) = "Next edit applied: lines=${range(edit.startLine, edit.endLine)} replacementLines=${replacementLines(edit)}"

    fun dismissed(reason: String) = "Next edit dismissed: reason=$reason"

    private fun range(start: Int, end: Int) = if (start == end) "$start" else "$start-$end"

    private fun replacementLines(edit: NextEdit) = if (edit.newText.isEmpty()) 0 else edit.newText.lines().size
}
