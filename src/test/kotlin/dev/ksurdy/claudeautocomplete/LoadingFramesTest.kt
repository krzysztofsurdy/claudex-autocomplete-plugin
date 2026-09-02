package dev.ksurdy.claudeautocomplete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadingFramesTest {
    @Test
    fun brailleFramesCycleThroughEightAndWrap() {
        assertEquals(8, LoadingFrames.BRAILLE.size)
        assertEquals("⢎⡰", LoadingFrames.frame(0, true))
        assertEquals("⢎⡡", LoadingFrames.frame(1, true))
        assertEquals("⢆⡱", LoadingFrames.frame(7, true))
        assertEquals("⢎⡰", LoadingFrames.frame(8, true))
        assertEquals("⢆⡱", LoadingFrames.frame(-1, true))
    }

    @Test
    fun allBrailleFramesHaveTwoChars() {
        assertTrue(LoadingFrames.BRAILLE.all { it.length == 2 })
    }

    @Test
    fun dotsFallbackCyclesThroughThree() {
        assertEquals("·  ", LoadingFrames.frame(0, false))
        assertEquals("···", LoadingFrames.frame(2, false))
        assertEquals("·  ", LoadingFrames.frame(3, false))
    }

    @Test
    fun elapsedIsHiddenBeforeOneAndAHalfSeconds() {
        assertNull(LoadingFrames.elapsed(0))
        assertNull(LoadingFrames.elapsed(1499))
    }

    @Test
    fun elapsedShowsOneDecimalUnderTenSeconds() {
        assertEquals(" 1.5s", LoadingFrames.elapsed(1500))
        assertEquals(" 1.8s", LoadingFrames.elapsed(1840))
        assertEquals(" 9.9s", LoadingFrames.elapsed(9940))
    }

    @Test
    fun elapsedShowsWholeSecondsFromTenSeconds() {
        assertEquals(" 10s", LoadingFrames.elapsed(10_000))
        assertEquals(" 12s", LoadingFrames.elapsed(12_900))
    }

    @Test
    fun textCombinesFrameAndElapsed() {
        assertEquals("⢎⡡", LoadingFrames.text(1, 100, true))
        assertEquals("⢎⡡ 1.8s", LoadingFrames.text(1, 1840, true))
    }
}
