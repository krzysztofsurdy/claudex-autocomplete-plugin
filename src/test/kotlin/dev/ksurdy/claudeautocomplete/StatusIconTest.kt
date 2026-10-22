package dev.ksurdy.claudeautocomplete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusIconTest {
    private fun state(status: ClaudeStatus, enabled: Boolean = true, consent: Boolean = true) =
        StatusIcon.state(enabled, consent, status)

    @Test
    fun idleStatesAreReady() {
        assertEquals(StatusIconState.Ready, state(ClaudeStatus.Ready))
        assertEquals(StatusIconState.Ready, state(ClaudeStatus.Waiting(0)))
        assertEquals(StatusIconState.Ready, state(ClaudeStatus.Done(700, 0)))
    }

    @Test
    fun thinkingIsThinking() {
        assertEquals(StatusIconState.Thinking, state(ClaudeStatus.Thinking(0)))
    }

    @Test
    fun failuresAndLimitsAreOff() {
        assertEquals(StatusIconState.Off, state(ClaudeStatus.Error("not logged in")))
        assertEquals(StatusIconState.Off, state(ClaudeStatus.LimitReached(null)))
    }

    @Test
    fun disabledOrWithoutConsentIsOffWhateverTheStatus() {
        listOf(ClaudeStatus.Ready, ClaudeStatus.Waiting(0), ClaudeStatus.Thinking(0), ClaudeStatus.Done(1, 0)).forEach {
            assertEquals(StatusIconState.Off, state(it, enabled = false))
            assertEquals(StatusIconState.Off, state(it, consent = false))
            assertEquals(StatusIconState.Off, state(it, enabled = false, consent = false))
        }
    }

    @Test
    fun onlyThinkingBlinks() {
        assertTrue(StatusIcon.isBlinking(StatusIconState.Thinking))
        assertFalse(StatusIcon.isBlinking(StatusIconState.Ready))
        assertFalse(StatusIcon.isBlinking(StatusIconState.Off))
    }

    @Test
    fun readyIsAlwaysColouredAndOffAlwaysGrey() {
        (0..5).forEach {
            assertTrue(StatusIcon.isColoured(StatusIconState.Ready, it))
            assertFalse(StatusIcon.isColoured(StatusIconState.Off, it))
        }
    }

    @Test
    fun thinkingAlternatesStartingColoured() {
        val phases = (0..5).map { StatusIcon.isColoured(StatusIconState.Thinking, it) }
        assertEquals(listOf(true, false, true, false, true, false), phases)
    }

    @Test
    fun blinksTwiceASecond() {
        assertEquals(500, StatusIcon.BLINK_MS)
    }
}
