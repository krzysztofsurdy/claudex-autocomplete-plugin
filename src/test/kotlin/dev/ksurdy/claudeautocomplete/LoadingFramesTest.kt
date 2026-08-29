package dev.ksurdy.claudeautocomplete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadingFramesTest {
    @Test
    fun cyclesThroughThreeFrames() {
        assertEquals("·  ", LoadingFrames.frame(0))
        assertEquals("·· ", LoadingFrames.frame(1))
        assertEquals("···", LoadingFrames.frame(2))
        assertEquals(LoadingFrames.frame(0), LoadingFrames.frame(3))
    }

    @Test
    fun handlesNegativeTicks() {
        assertEquals(LoadingFrames.frame(2), LoadingFrames.frame(-1))
    }

    @Test
    fun allFramesHaveEqualLengthSoWidthIsStable() {
        assertTrue((0..5).all { LoadingFrames.frame(it).length == LoadingFrames.widest.length })
    }
}
