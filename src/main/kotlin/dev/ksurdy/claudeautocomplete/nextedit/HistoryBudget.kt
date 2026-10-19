package dev.ksurdy.claudeautocomplete.nextedit

object HistoryBudget {
    const val DEFAULT_TOKENS = 1000

    fun select(hunks: List<EditHunk>, maxTokens: Int = DEFAULT_TOKENS): List<EditHunk> {
        var used = 0
        val kept = ArrayList<EditHunk>()
        for (hunk in hunks.asReversed()) {
            used += tokens(hunk)
            if (used > maxTokens) break
            kept += hunk
        }
        return kept.asReversed()
    }

    fun tokens(hunk: EditHunk): Int = (EditDiff.render(hunk).sumOf { it.length + 1 } + 3) / 4
}
