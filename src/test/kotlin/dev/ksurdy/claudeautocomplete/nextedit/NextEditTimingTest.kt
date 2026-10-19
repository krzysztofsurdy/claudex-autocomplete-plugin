package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextEditTimingTest {
    private var now = 1_000L
    private val timing = NextEditTiming { now }

    @Test
    fun `starts from the base pause`() {
        assertEquals(200, timing.pauseMs(200))
    }

    @Test
    fun `clamps the pause between 50 and 3000 ms`() {
        assertEquals(50, timing.pauseMs(0))
        assertEquals(3000, timing.pauseMs(9000))
    }

    @Test
    fun `each rejection multiplies the next pause by 1_5`() {
        timing.recordRejection(3, 200)
        assertEquals(300, timing.pauseMs(200))
        timing.recordRejection(3, 200)
        assertEquals(450, timing.pauseMs(200))
    }

    @Test
    fun `each acceptance multiplies the next pause by 0_8`() {
        timing.recordAcceptance(200)
        assertEquals(160, timing.pauseMs(200))
        timing.recordAcceptance(200)
        assertEquals(128, timing.pauseMs(200))
    }

    @Test
    fun `repeated rejections stop at the ceiling and one acceptance steps back from it`() {
        repeat(20) { timing.recordRejection(1, 200) }
        assertEquals(3000, timing.pauseMs(200))
        timing.recordAcceptance(200)
        assertEquals(2400, timing.pauseMs(200))
    }

    @Test
    fun `repeated acceptances stop at the floor`() {
        repeat(20) { timing.recordAcceptance(200) }
        assertEquals(50, timing.pauseMs(200))
    }

    @Test
    fun `a rejection from a zero base starts at the floor and grows from there`() {
        timing.recordRejection(1, 0)
        assertEquals(75, timing.pauseMs(0))
    }

    @Test
    fun `no request for 5 seconds after a rejection`() {
        assertNull(timing.requestBlock())
        timing.recordRejection(3, 200)
        assertEquals(SkipReason.Cooldown, timing.requestBlock())
        now += 4_999
        assertEquals(SkipReason.Cooldown, timing.requestBlock())
        now += 1
        assertNull(timing.requestBlock())
    }

    @Test
    fun `a rejected line stays blocked for 30 seconds while other lines are free`() {
        timing.recordRejection(7, 200)
        now += 6_000
        assertEquals(SkipReason.LineCooldown, timing.lineBlock(7))
        assertNull(timing.lineBlock(8))
        now += 23_999
        assertEquals(SkipReason.LineCooldown, timing.lineBlock(7))
        now += 1
        assertNull(timing.lineBlock(7))
    }

    @Test
    fun `acceptance does not start a cooldown`() {
        timing.recordAcceptance(200)
        assertNull(timing.requestBlock())
        assertNull(timing.lineBlock(1))
    }

    @Test
    fun `waits two more seconds with the caret at the end of a typed line`() {
        assertEquals(2_000, timing.extraWaitMs("    return \$a", 13))
    }

    @Test
    fun `no extra wait mid line or on a blank line`() {
        assertEquals(0, timing.extraWaitMs("    return \$a;", 8))
        assertEquals(0, timing.extraWaitMs("    ", 4))
        assertEquals(0, timing.extraWaitMs("", 0))
    }
}
