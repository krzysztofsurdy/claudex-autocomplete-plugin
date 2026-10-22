package dev.ksurdy.claudeautocomplete.nextedit

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InlineWaitTest {
    private var sleeps = 0

    private fun await(maxWaits: Int, busyChecks: Int): Boolean = runBlocking {
        var checks = 0
        InlineWait.await(maxWaits, isBusy = { checks++ < busyChecks }, onWait = { sleeps++ }, sleep = {})
    }

    @Test
    fun `goes straight on when inline completion is idle`() {
        assertTrue(await(maxWaits = 5, busyChecks = 0))
        assertEquals(0, sleeps)
    }

    @Test
    fun `waits while inline completion is busy and goes on when it is done`() {
        assertTrue(await(maxWaits = 5, busyChecks = 3))
        assertEquals(3, sleeps)
    }

    @Test
    fun `gives up after the maximum number of waits`() {
        assertFalse(await(maxWaits = 4, busyChecks = Int.MAX_VALUE))
        assertEquals(4, sleeps)
    }

    @Test
    fun `succeeds when it becomes idle exactly after the last wait`() {
        assertTrue(await(maxWaits = 4, busyChecks = 4))
        assertEquals(4, sleeps)
    }
}
