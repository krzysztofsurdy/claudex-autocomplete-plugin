package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    private val indented = "<?php\nclass A\n{\n    public function f(int \$a)\n    {\n        return \$a;\n    }\n}"

    @Test
    fun `restores the first line indentation a model dropped in a block`() {
        val edit = NextEditTarget.choose(ParsedEdit(4, 6, "public function f(int \$a, int \$b)\n    {\n        return \$a;"), indented, caretLine = 1)!!
        assertEquals("    public function f(int \$a, int \$b)\n    {\n        return \$a;", edit.newText)
    }

    @Test
    fun `restores the indentation of a single line with the same first word`() {
        val edit = NextEditTarget.choose(ParsedEdit(6, 6, "return \$b;"), indented, caretLine = 1)!!
        assertEquals("        return \$b;", edit.newText)
    }

    @Test
    fun `keeps a deliberate dedent`() {
        val edit = NextEditTarget.choose(ParsedEdit(4, 5, "public function f()\n{"), indented, caretLine = 1)!!
        assertEquals("public function f()\n{", edit.newText)
    }

    @Test
    fun `keeps an unindented single line that starts differently`() {
        val edit = NextEditTarget.choose(ParsedEdit(6, 6, "echo 1;"), indented, caretLine = 1)!!
        assertEquals("echo 1;", edit.newText)
    }

    private fun dropped(parsed: ParsedEdit?, caretLine: Int = 1, maxLines: Int = NextEditTarget.MAX_LINES): DropReason =
        assertIs<TargetOutcome.Dropped>(NextEditTarget.evaluate(parsed, text, caretLine, maxLines)).reason

    @Test
    fun `evaluate names the reason an edit is dropped`() {
        assertEquals(DropReason.Unparsable, dropped(null))
        assertEquals(DropReason.OutsideFile, dropped(ParsedEdit(6, 6, "x")))
        assertEquals(DropReason.OutsideFile, dropped(ParsedEdit(4, 9, "x")))
        assertEquals(DropReason.TooLong, dropped(ParsedEdit(1, 4, "x"), maxLines = 3))
        assertEquals(DropReason.CaretLine, dropped(ParsedEdit(2, 4, "x"), caretLine = 3))
        assertEquals(DropReason.NoOp, dropped(ParsedEdit(3, 3, "three")))
    }

    @Test
    fun `evaluate returns the same edit as choose when nothing is dropped`() {
        val parsed = ParsedEdit(3, 3, "THREE")
        val outcome = assertIs<TargetOutcome.Chosen>(NextEditTarget.evaluate(parsed, text, caretLine = 1))
        assertEquals(NextEditTarget.choose(parsed, text, caretLine = 1), outcome.edit)
    }
}
