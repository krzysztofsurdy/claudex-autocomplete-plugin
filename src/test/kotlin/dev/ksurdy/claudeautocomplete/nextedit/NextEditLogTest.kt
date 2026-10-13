package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NextEditLogTest {
    private val edit = NextEdit(12, 14, 100, 160, "secret old code\nmore\nlines", "secret new code\nsecond")

    @Test
    fun `request line carries language and edit count`() {
        assertEquals("Requesting next edit: language=PHP edits=3", NextEditLog.requested("PHP", 3))
    }

    @Test
    fun `received line carries the time and the parsed line range`() {
        assertEquals("Next edit received: ms=412 target=12-14", NextEditLog.received(412, ParsedEdit(12, 14, "x")))
        assertEquals("Next edit received: ms=7 target=9", NextEditLog.received(7, ParsedEdit(9, 9, "x")))
    }

    @Test
    fun `received line says none when nothing was parsed`() {
        assertEquals("Next edit received: ms=7 target=none", NextEditLog.received(7, null))
    }

    @Test
    fun `empty and failed results are logged by shape`() {
        assertEquals("Next edit empty: ms=5", NextEditLog.empty(5))
        assertEquals("Next edit failed: kind=Timeout ms=8000", NextEditLog.failed("Timeout", 8000))
        assertEquals("Next edit cancelled: ms=30", NextEditLog.cancelled(30))
    }

    @Test
    fun `dropped line names the reason`() {
        assertEquals("Next edit dropped: reason=stale document", NextEditLog.dropped("stale document"))
    }

    @Test
    fun `drop reasons from the target choice have readable labels`() {
        assertEquals("caret line", NextEditLog.reason(DropReason.CaretLine, "<edit start_line=\"1\">x</edit>"))
        assertEquals("too long", NextEditLog.reason(DropReason.TooLong, ""))
        assertEquals("no-op", NextEditLog.reason(DropReason.NoOp, ""))
        assertEquals("outside file", NextEditLog.reason(DropReason.OutsideFile, ""))
        assertEquals("unparsable", NextEditLog.reason(DropReason.Unparsable, "garbage"))
    }

    @Test
    fun `a none reply is told apart from an unparsable one`() {
        assertEquals("model replied none", NextEditLog.reason(DropReason.Unparsable, "<none/>"))
        assertEquals("model replied none", NextEditLog.reason(DropReason.Unparsable, "Sure.\n<none />"))
    }

    @Test
    fun `shown, jumped and applied lines carry ranges and sizes only`() {
        assertEquals("Next edit shown: lines=12-14 replacementLines=2", NextEditLog.shown(edit))
        assertEquals("Next edit jumped: line=12", NextEditLog.jumped(edit))
        assertEquals("Next edit applied: lines=12-14 replacementLines=2", NextEditLog.applied(edit))
    }

    @Test
    fun `a single line range is printed as one number and a deletion has no replacement lines`() {
        val deletion = edit.copy(startLine = 5, endLine = 5, newText = "")
        assertEquals("Next edit shown: lines=5 replacementLines=0", NextEditLog.shown(deletion))
    }

    @Test
    fun `dismissed line names the reason`() {
        assertEquals("Next edit dismissed: reason=escape", NextEditLog.dismissed("escape"))
    }

    @Test
    fun `no line leaks code content`() {
        val lines = listOf(NextEditLog.shown(edit), NextEditLog.jumped(edit), NextEditLog.applied(edit))
        lines.forEach { assertFalse(it.contains("secret")) }
    }
}
