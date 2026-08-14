package dev.ksurdy.claudeautocomplete.ide

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.runner.JUnitCore

class IdeTestsRunnerTest {
    @Test
    fun platformTestsPass() {
        val result = JUnitCore().run(ClaudeInlineCompletionIdeTest::class.java)
        val report = result.failures.joinToString("\n\n") { it.testHeader + "\n" + it.trace }
        assertTrue(result.wasSuccessful(), report)
    }
}
