package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryBudgetTest {
    private fun hunk(line: Int, size: Int) = EditHunk(line, "a".repeat(size), "b".repeat(size))

    @Test
    fun `keeps everything that fits`() {
        val hunks = listOf(hunk(1, 20), hunk(2, 20))
        assertEquals(hunks, HistoryBudget.select(hunks, 1000))
    }

    @Test
    fun `drops the oldest hunks first`() {
        val hunks = (1..10).map { hunk(it, 100) }
        val kept = HistoryBudget.select(hunks, 200)
        assertTrue(kept.isNotEmpty())
        assertTrue(kept.size < hunks.size)
        assertEquals(hunks.takeLast(kept.size), kept)
    }

    @Test
    fun `stays within the budget estimated as chars over four`() {
        val hunks = (1..30).map { hunk(it, 80) }
        val kept = HistoryBudget.select(hunks, 1000)
        assertTrue(kept.sumOf { HistoryBudget.tokens(it) } <= 1000)
        assertEquals(hunks.takeLast(kept.size), kept)
        assertEquals(23, kept.size)
    }

    @Test
    fun `a newer hunk is never skipped for an older one that happens to fit`() {
        val hunks = listOf(hunk(1, 10), hunk(2, 400), hunk(3, 10))
        assertEquals(listOf(3), HistoryBudget.select(hunks, 60).map { it.startLine })
    }

    @Test
    fun `nothing is kept when even the newest hunk is over budget`() {
        assertEquals(emptyList(), HistoryBudget.select(listOf(hunk(1, 400)), 10))
    }

    @Test
    fun `token estimate is the rendered characters over four rounded up`() {
        assertEquals(3, HistoryBudget.tokens(EditHunk(1, "ab", "cd")))
    }
}
