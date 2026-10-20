package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextEditTargetTest {
    private val text = "one\ntwo\nthree\nfour\nfive"

    @Test
    fun `maps lines to offsets`() {
        val edit = NextEditTarget.choose(ParsedEdit(3, 3, "THREE"), text, caretLine = 1)!!
        assertEquals(8, edit.startOffset)
        assertEquals(13, edit.endOffset)
        assertEquals("three", edit.oldText)
        assertEquals("THREE", edit.newText)
        assertEquals(3, edit.startLine)
        assertEquals(3, edit.endLine)
    }

    @Test
    fun `maps a multi line range`() {
        val edit = NextEditTarget.choose(ParsedEdit(2, 3, "x\ny\nz"), text, caretLine = 5)!!
        assertEquals("two\nthree", edit.oldText)
        assertEquals(4, edit.startOffset)
        assertEquals(13, edit.endOffset)
    }

    @Test
    fun `the last line has no trailing newline`() {
        val edit = NextEditTarget.choose(ParsedEdit(5, 5, "FIVE"), text, caretLine = 1)!!
        assertEquals("five", edit.oldText)
        assertEquals(text.length, edit.endOffset)
    }

    @Test
    fun `no edit parsed gives no target`() {
        assertNull(NextEditTarget.choose(null, text, caretLine = 1))
    }

    @Test
    fun `edits on the caret line are left to inline completion`() {
        assertNull(NextEditTarget.choose(ParsedEdit(3, 3, "x"), text, caretLine = 3))
        assertNull(NextEditTarget.choose(ParsedEdit(2, 4, "x"), text, caretLine = 3))
    }

    @Test
    fun `edits next to the caret line are kept`() {
        assertEquals(4, NextEditTarget.choose(ParsedEdit(4, 4, "x"), text, caretLine = 3)!!.startLine)
    }

    @Test
    fun `out of range lines are rejected`() {
        assertNull(NextEditTarget.choose(ParsedEdit(6, 6, "x"), text, caretLine = 1))
        assertNull(NextEditTarget.choose(ParsedEdit(4, 9, "x"), text, caretLine = 1))
    }

    @Test
    fun `ranges longer than the limit are rejected`() {
        val long = (1..40).joinToString("\n") { "l$it" }
        assertNull(NextEditTarget.choose(ParsedEdit(2, 30, "x"), long, caretLine = 1, maxLines = 10))
    }

    @Test
    fun `an edit that changes nothing is rejected`() {
        assertNull(NextEditTarget.choose(ParsedEdit(3, 3, "three"), text, caretLine = 1))
    }

    @Test
    fun `deleting a middle line removes its newline`() {
        val edit = NextEditTarget.choose(ParsedEdit(3, 3, ""), text, caretLine = 1)!!
        assertEquals(8, edit.startOffset)
        assertEquals(14, edit.endOffset)
        assertEquals("", edit.newText)
    }

    @Test
    fun `deleting the last line removes the newline before it`() {
        val edit = NextEditTarget.choose(ParsedEdit(5, 5, ""), text, caretLine = 1)!!
        assertEquals(text.length - 5, edit.startOffset)
        assertEquals(text.length, edit.endOffset)
    }
}
