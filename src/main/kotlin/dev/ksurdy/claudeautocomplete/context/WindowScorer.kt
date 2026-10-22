package dev.ksurdy.claudeautocomplete.context

class ScoredWindow(val startLine: Int, val endLine: Int, val score: Double, val shared: Int)

class WindowScorer(private val windowLines: Int = WINDOW_LINES, private val stepLines: Int = STEP_LINES) {
    var operations: Long = 0
        private set

    fun best(file: TokenizedFile, querySize: Int): ScoredWindow? {
        if (file.lineCount == 0 || querySize == 0) return null
        val counts = IntArray(file.vocabularySize)
        var distinct = 0
        var shared = 0
        var from = 0
        var to = 0
        var best: ScoredWindow? = null
        for (start in windowStarts(file.lineCount)) {
            val end = minOf(file.lineCount, start + windowLines)
            val newFrom = file.lineTokenStart[start]
            val newTo = file.lineTokenStart[end]
            while (from < newFrom) {
                val id = file.tokens[from++]
                if (--counts[id] == 0) {
                    distinct--
                    if (file.isQueryToken[id]) shared--
                }
                operations++
            }
            while (to < newTo) {
                val id = file.tokens[to++]
                if (counts[id]++ == 0) {
                    distinct++
                    if (file.isQueryToken[id]) shared++
                }
                operations++
            }
            if (shared == 0) continue
            val score = shared.toDouble() / (querySize + distinct - shared)
            if (best == null || score > best.score) best = ScoredWindow(start, end, score, shared)
        }
        return best
    }

    private fun windowStarts(lineCount: Int): List<Int> {
        if (lineCount <= windowLines) return listOf(0)
        val last = lineCount - windowLines
        val starts = ArrayList<Int>()
        var start = 0
        while (start < last) {
            starts += start
            start += stepLines
        }
        starts += last
        return starts
    }

    companion object {
        const val WINDOW_LINES = 60
        const val STEP_LINES = 5
    }
}
