package dev.ksurdy.claudeautocomplete

import kotlin.test.Test
import kotlin.test.assertEquals

class OptionLabelsTest {
    @Test
    fun capitalizesStoredValues() {
        assertEquals("Low", OptionLabels.display("low"))
        assertEquals("Medium", OptionLabels.display("medium"))
        assertEquals("Extra high", OptionLabels.display("xhigh"))
        assertEquals("Auto", OptionLabels.display("auto"))
        assertEquals("Never", OptionLabels.display("never"))
        assertEquals("None", OptionLabels.display("none"))
    }

    @Test
    fun leavesUnknownValuesUntouched() {
        assertEquals("custom", OptionLabels.display("custom"))
    }
}
