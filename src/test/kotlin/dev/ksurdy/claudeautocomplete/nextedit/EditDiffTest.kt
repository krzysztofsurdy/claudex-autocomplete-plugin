package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals

class EditDiffTest {
    @Test
    fun `a one line change shows the removed and the added line with their numbers`() {
        assertEquals(listOf("-4|foo(\$a)", "+4|foo(\$ab)"), EditDiff.render(EditHunk(4, "foo(\$a)", "foo(\$ab)")))
    }

    @Test
    fun `lines the hunk leaves alone are not repeated`() {
        val hunk = EditHunk(10, "a\nb\nc\nd", "a\nB\nc\nd")
        assertEquals(listOf("-11|b", "+11|B"), EditDiff.render(hunk))
    }

    @Test
    fun `an insertion shows only added lines`() {
        assertEquals(listOf("+6|new", "+7|new2"), EditDiff.render(EditHunk(5, "x", "x\nnew\nnew2")))
    }

    @Test
    fun `a deletion shows only removed lines`() {
        assertEquals(listOf("-2|b", "-3|c"), EditDiff.render(EditHunk(1, "a\nb\nc", "a")))
    }

    @Test
    fun `a blank line replaced by text keeps both sides`() {
        assertEquals(listOf("-3|", "+3|text"), EditDiff.render(EditHunk(3, "", "text")))
    }

    @Test
    fun `indentation is kept`() {
        assertEquals(listOf("-2|    a", "+2|        a"), EditDiff.render(EditHunk(2, "    a", "        a")))
    }
}
