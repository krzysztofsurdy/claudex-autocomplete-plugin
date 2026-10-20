package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditHistoryTest {
    @Test
    fun `records a hunk`() {
        val history = EditHistory()
        history.record(3, "foo(\$a)", "foo(\$ab)")
        assertEquals(listOf(EditHunk(3, "foo(\$a)", "foo(\$ab)")), history.hunks())
    }

    @Test
    fun `ignores edits that change nothing`() {
        val history = EditHistory()
        history.record(1, "same", "same")
        assertTrue(history.hunks().isEmpty())
    }

    @Test
    fun `chained typing on one line merges into a single hunk`() {
        val history = EditHistory()
        history.record(5, "name", "names")
        history.record(5, "names", "namesp")
        history.record(5, "namesp", "namespa")
        assertEquals(listOf(EditHunk(5, "name", "namespa")), history.hunks())
    }

    @Test
    fun `typing that is undone leaves no hunk`() {
        val history = EditHistory()
        history.record(5, "name", "names")
        history.record(5, "names", "name")
        assertTrue(history.hunks().isEmpty())
    }

    @Test
    fun `edits on different lines stay separate`() {
        val history = EditHistory()
        history.record(2, "a", "b")
        history.record(9, "c", "d")
        assertEquals(listOf(EditHunk(2, "a", "b"), EditHunk(9, "c", "d")), history.hunks())
    }

    @Test
    fun `keeps only the most recent hunks`() {
        val history = EditHistory(maxEdits = 3)
        (1..5).forEach { history.record(it * 10, "a$it", "b$it") }
        assertEquals(listOf(30, 40, 50), history.hunks().map { it.startLine })
    }

    @Test
    fun `inserted lines shift hunks below`() {
        val history = EditHistory()
        history.record(20, "x", "y")
        history.record(5, "line", "line\nextra")
        assertEquals(listOf(21, 5), history.hunks().map { it.startLine })
    }

    @Test
    fun `removed lines shift hunks below up`() {
        val history = EditHistory()
        history.record(20, "x", "y")
        history.record(5, "a\nb\nc", "a")
        assertEquals(listOf(18, 5), history.hunks().map { it.startLine })
    }

    @Test
    fun `hunks above a change keep their line`() {
        val history = EditHistory()
        history.record(2, "x", "y")
        history.record(8, "line", "line\nextra")
        assertEquals(listOf(2, 8), history.hunks().map { it.startLine })
    }

    @Test
    fun `oversized hunks are dropped`() {
        val history = EditHistory(maxHunkLines = 3, maxHunkChars = 50)
        history.record(1, "a", (1..4).joinToString("\n") { "l$it" })
        history.record(2, "a", "x".repeat(51))
        assertTrue(history.hunks().isEmpty())
    }

    @Test
    fun `an oversized hunk still shifts the lines below`() {
        val history = EditHistory(maxHunkLines = 3)
        history.record(20, "x", "y")
        history.record(1, "a", (1..6).joinToString("\n") { "l$it" })
        assertEquals(listOf(25), history.hunks().map { it.startLine })
    }

    @Test
    fun `clear forgets everything`() {
        val history = EditHistory()
        history.record(1, "a", "b")
        history.clear()
        assertTrue(history.hunks().isEmpty())
    }
}
