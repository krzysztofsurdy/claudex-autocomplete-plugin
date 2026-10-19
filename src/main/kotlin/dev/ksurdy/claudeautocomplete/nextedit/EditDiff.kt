package dev.ksurdy.claudeautocomplete.nextedit

object EditDiff {
    fun render(hunk: EditHunk): List<String> {
        val before = hunk.before.split('\n')
        val after = hunk.after.split('\n')
        val limit = minOf(before.size, after.size)
        val prefix = (0 until limit).takeWhile { before[it] == after[it] }.size
        val suffix = (0 until limit - prefix).takeWhile { before[before.size - 1 - it] == after[after.size - 1 - it] }.size
        val removed = before.subList(prefix, before.size - suffix).mapIndexed { index, line -> "-${hunk.startLine + prefix + index}|$line" }
        val added = after.subList(prefix, after.size - suffix).mapIndexed { index, line -> "+${hunk.startLine + prefix + index}|$line" }
        return removed + added
    }
}
