package dev.ksurdy.claudeautocomplete.nextedit

data class EditHunk(val startLine: Int, val before: String, val after: String)

class EditHistory(
    private val maxEdits: Int = DEFAULT_MAX_EDITS,
    private val maxHunkLines: Int = DEFAULT_MAX_HUNK_LINES,
    private val maxHunkChars: Int = DEFAULT_MAX_HUNK_CHARS,
) {
    private val hunks = ArrayList<EditHunk>()

    @Synchronized
    fun record(startLine: Int, before: String, after: String) {
        if (before == after) return
        shiftBelow(startLine + lineCount(before), lineCount(after) - lineCount(before))
        val last = hunks.lastOrNull()
        val merged = if (last != null && last.startLine == startLine && last.after == before) {
            hunks.removeAt(hunks.lastIndex)
            EditHunk(startLine, last.before, after)
        } else {
            EditHunk(startLine, before, after)
        }
        if (merged.before == merged.after || !isSmall(merged)) return
        hunks += merged
        while (hunks.size > maxEdits) hunks.removeAt(0)
    }

    @Synchronized
    fun hunks(): List<EditHunk> = hunks.toList()

    @Synchronized
    fun clear() = hunks.clear()

    private fun shiftBelow(firstUnaffectedLine: Int, delta: Int) {
        if (delta == 0) return
        hunks.replaceAll { if (it.startLine >= firstUnaffectedLine) it.copy(startLine = it.startLine + delta) else it }
    }

    private fun isSmall(hunk: EditHunk): Boolean =
        lineCount(hunk.before) <= maxHunkLines && lineCount(hunk.after) <= maxHunkLines &&
            hunk.before.length <= maxHunkChars && hunk.after.length <= maxHunkChars

    private fun lineCount(text: String): Int = text.count { it == '\n' } + 1

    companion object {
        const val DEFAULT_MAX_EDITS = 5
        const val DEFAULT_MAX_HUNK_LINES = 12
        const val DEFAULT_MAX_HUNK_CHARS = 600
    }
}
