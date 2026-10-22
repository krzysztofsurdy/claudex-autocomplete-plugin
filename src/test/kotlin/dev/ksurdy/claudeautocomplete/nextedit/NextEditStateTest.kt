package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextEditStateTest {
    private val edit = NextEdit(3, 3, 8, 13, "three", "THREE")

    @Test
    fun `starts hidden and tab does nothing`() {
        val state = NextEditState()
        assertFalse(state.isVisible)
        assertEquals(TabOutcome.None, state.onTab())
    }

    @Test
    fun `first tab jumps and second tab applies`() {
        val state = NextEditState()
        state.show(edit)
        assertTrue(state.isVisible)
        assertEquals(TabOutcome.Jump(8), state.onTab())
        assertEquals(NextEditState.Phase.Jumped, state.phase)
        assertTrue(state.isVisible)
        assertEquals(TabOutcome.Apply(edit), state.onTab())
        assertFalse(state.isVisible)
        assertEquals(TabOutcome.None, state.onTab())
    }

    @Test
    fun `dismiss hides and forgets the edit`() {
        val state = NextEditState()
        state.show(edit)
        state.dismiss()
        assertFalse(state.isVisible)
        assertNull(state.edit)
        assertEquals(TabOutcome.None, state.onTab())
    }

    @Test
    fun `showing a new edit restarts at the jump step`() {
        val state = NextEditState()
        state.show(edit)
        state.onTab()
        val other = edit.copy(startOffset = 0, endOffset = 3)
        state.show(other)
        assertEquals(NextEditState.Phase.Shown, state.phase)
        assertEquals(TabOutcome.Jump(0), state.onTab())
    }
}
