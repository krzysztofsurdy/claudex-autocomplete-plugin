package dev.ksurdy.claudeautocomplete.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CaretQueryTest {
    @Test
    fun `takes the last lines before the caret and the rest of the current line`() {
        val prefix = (1..100).joinToString("\n") { "line$it" } + "\nfoo("
        val query = CaretQuery.around(prefix, "bar)\nnext line", 60)
        val lines = query.lines()
        assertEquals(60, lines.size)
        assertEquals("line42", lines.first())
        assertEquals("foo(bar)", lines.last())
        assertFalse(query.contains("next line"))
    }

    @Test
    fun `short prefix is kept whole`() {
        assertEquals("a\nb", CaretQuery.around("a\nb", "", 60))
    }

    @Test
    fun `empty prefix gives the current line from the suffix`() {
        assertEquals("rest", CaretQuery.around("", "rest\nmore", 60))
    }

    @Test
    fun `caret at the start of a line counts that empty line`() {
        assertEquals("a\nb\nc", CaretQuery.around("a\nb\n", "c\nd", 60))
    }
}
