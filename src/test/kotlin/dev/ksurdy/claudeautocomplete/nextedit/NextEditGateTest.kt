package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextEditGateTest {
    private var now = 0L
    private val gate = NextEditGate(NextEditTiming { now }, RejectionMemory({ now }), CaretTrigger { now }) { "a.php" }
    private val edit = NextEdit(3, 3, 20, 40, "    return \$a;", "    return \$ab;")

    @Test
    fun `a fresh gate lets everything through`() {
        assertNull(gate.beforeRequest())
        assertNull(gate.beforeShow(edit))
    }

    @Test
    fun `a rejection blocks requests for 5 seconds`() {
        gate.rejected(edit, 200)
        assertEquals(SkipReason.Cooldown, gate.beforeRequest())
        now = 5_000
        assertNull(gate.beforeRequest())
    }

    @Test
    fun `a rejected line gets no other suggestion for a while`() {
        gate.rejected(edit, 200)
        now = 6_000
        assertEquals(SkipReason.LineCooldown, gate.beforeShow(edit.copy(newText = "other")))
        now = 30_000
        assertNull(gate.beforeShow(edit.copy(newText = "other")))
    }

    @Test
    fun `the same suggestion stays blocked after the line cooldown`() {
        gate.rejected(edit, 200)
        now = 60_000
        assertEquals(SkipReason.Remembered, gate.beforeShow(edit))
        now = 300_000
        assertNull(gate.beforeShow(edit))
    }

    @Test
    fun `an acceptance blocks nothing and speeds up the pause`() {
        gate.accepted(200)
        assertNull(gate.beforeRequest())
        assertNull(gate.beforeShow(edit))
        assertEquals(160, gate.pauseMs(200))
    }

    @Test
    fun `an edit opens the caret move window`() {
        assertFalse(gate.caretMoved(1, 5))
        gate.edited()
        assertTrue(gate.caretMoved(1, 5))
    }
}
