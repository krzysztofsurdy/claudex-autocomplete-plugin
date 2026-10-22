package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaretTriggerTest {
    private var now = 0L
    private val trigger = CaretTrigger { now }

    @Test
    fun `a move to another line right after an edit triggers`() {
        trigger.recordEdit()
        now += 3_000
        assertTrue(trigger.shouldRequest(2, 9))
    }

    @Test
    fun `a move within the same line does not trigger`() {
        trigger.recordEdit()
        assertFalse(trigger.shouldRequest(4, 4))
    }

    @Test
    fun `ten seconds after the last edit it stops triggering`() {
        trigger.recordEdit()
        now = 10_000
        assertTrue(trigger.shouldRequest(1, 2))
        now = 10_001
        assertFalse(trigger.shouldRequest(1, 2))
    }

    @Test
    fun `a new edit restarts the window`() {
        trigger.recordEdit()
        now = 9_000
        trigger.recordEdit()
        now = 15_000
        assertTrue(trigger.shouldRequest(1, 2))
    }

    @Test
    fun `without any edit nothing triggers`() {
        assertFalse(trigger.shouldRequest(1, 2))
    }
}
