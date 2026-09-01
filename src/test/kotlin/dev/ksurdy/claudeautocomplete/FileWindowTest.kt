package dev.ksurdy.claudeautocomplete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileWindowTest {
    private fun lines(count: Int, separator: String = "\n") = (1..count).joinToString(separator) { "l$it" }

    private fun window(text: String, offset: Int, mode: String = "auto", whole: Int = 5, around: Int = 2, cap: Int = 1_000_000) =
        FileWindow.compute(text, offset, mode, whole, around, cap)

    @Test
    fun autoUsesWholeFileAtThreshold() {
        val text = lines(5)
        val w = window(text, text.indexOf("l3"))
        assertEquals(text, w.prefix + w.suffix)
        assertFalse(w.prefixTruncated)
        assertFalse(w.suffixTruncated)
    }

    @Test
    fun autoSwitchesToWindowOneLineAboveThreshold() {
        val text = lines(6)
        val offset = text.indexOf("l4")
        val w = window(text, offset)
        assertEquals("l2\nl3\n", w.prefix)
        assertEquals("l4\nl5\nl6", w.suffix)
        assertTrue(w.prefixTruncated)
        assertFalse(w.suffixTruncated)
    }

    @Test
    fun windowAtStartOfFileIsNotPrefixTruncated() {
        val text = lines(20)
        val w = window(text, 0, mode = "linesAround")
        assertEquals("", w.prefix)
        assertEquals("l1\nl2\nl3", w.suffix)
        assertFalse(w.prefixTruncated)
        assertTrue(w.suffixTruncated)
    }

    @Test
    fun windowAtEndOfFileIsNotSuffixTruncated() {
        val text = lines(20)
        val w = window(text, text.length, mode = "linesAround")
        assertEquals("l18\nl19\nl20", w.prefix)
        assertEquals("", w.suffix)
        assertTrue(w.prefixTruncated)
        assertFalse(w.suffixTruncated)
    }

    @Test
    fun windowKeepsPartialCaretLine() {
        val text = lines(20)
        val offset = text.indexOf("l10") + 1
        val w = window(text, offset, mode = "linesAround")
        assertEquals("l8\nl9\nl", w.prefix)
        assertEquals("10\nl11\nl12", w.suffix)
    }

    @Test
    fun wholeFileModeIgnoresLineCounts() {
        val text = lines(500)
        val w = window(text, 100, mode = "wholeFile")
        assertEquals(text, w.prefix + w.suffix)
    }

    @Test
    fun handlesCrlf() {
        val text = lines(20, "\r\n")
        val offset = text.indexOf("l10")
        val w = window(text, offset, mode = "linesAround")
        assertEquals("l8\r\nl9\r\n", w.prefix)
        assertEquals("l10\r\nl11\r\nl12", w.suffix)
    }

    @Test
    fun hardCapClipsAndReportsTruncation() {
        val text = "a".repeat(1000) + "|" + "b".repeat(1000)
        val w = window(text, 1000, mode = "wholeFile", cap = 100)
        assertTrue(w.prefix.length <= 60)
        assertTrue(w.suffix.length <= 40)
        assertTrue(w.prefix.all { it == 'a' })
        assertTrue(w.prefixTruncated)
        assertTrue(w.suffixTruncated)
    }

    @Test
    fun unknownModeFallsBackToAuto() {
        val text = lines(6)
        val w = window(text, text.indexOf("l4"), mode = "bogus")
        assertTrue(w.prefixTruncated)
    }
}
