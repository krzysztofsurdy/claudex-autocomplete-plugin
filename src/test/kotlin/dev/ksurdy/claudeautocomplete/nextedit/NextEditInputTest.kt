package dev.ksurdy.claudeautocomplete.nextedit

import dev.ksurdy.claudeautocomplete.FileWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NextEditInputTest {
    private val edits = listOf(EditHunk(2, "a", "b"))
    private val text = (1..10).joinToString("\n") { "line$it" }

    private fun create(offset: Int, mode: String = FileWindow.MODE_AUTO, wholeMax: Int = 1000, around: Int = 150, cap: Int = 200_000) =
        NextEditInput.create(edits, text, offset, mode, wholeMax, around, cap)

    @Test
    fun `small files are sent whole with the caret line`() {
        val input = create(offset = text.indexOf("line4"))
        assertEquals(text, input.windowText)
        assertEquals(1, input.firstLine)
        assertEquals(4, input.caretLine)
        assertEquals(edits, input.edits)
    }

    @Test
    fun `large files are windowed around the caret`() {
        val input = create(offset = text.indexOf("line6"), wholeMax = 5, around = 2)
        assertEquals("line4\nline5\nline6\nline7\nline8", input.windowText)
        assertEquals(4, input.firstLine)
        assertEquals(6, input.caretLine)
    }

    @Test
    fun `the window is clamped at the file edges`() {
        val input = create(offset = 0, wholeMax = 5, around = 2)
        assertEquals("line1\nline2\nline3", input.windowText)
        assertEquals(1, input.firstLine)
    }

    @Test
    fun `lines around mode always windows`() {
        val input = create(offset = text.indexOf("line5"), mode = FileWindow.MODE_LINES_AROUND, around = 1)
        assertEquals("line4\nline5\nline6", input.windowText)
    }

    @Test
    fun `the character cap shrinks the window`() {
        val input = create(offset = text.indexOf("line5"), cap = 20)
        assertTrue(input.windowText.length <= 20)
        assertTrue(input.windowText.contains("line5"))
    }
}
