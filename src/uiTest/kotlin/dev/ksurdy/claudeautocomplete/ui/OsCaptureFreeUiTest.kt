package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.sdk.waitForIndicators
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class OsCaptureFreeUiTest {
    @Test
    fun `an idle IDE past the heartbeat interval takes no screenshot`() {
        assumeTrue(System.getenv("CLAUDEX_SOAK") == "1", "set CLAUDEX_SOAK=1 to keep PhpStorm idle for over a minute and prove no screenshot job runs")
        val context = newPhpStormContext("captureSoak")
        val session = GuardedIde.start(context)
        var screenshotLines = emptyList<String>()
        try {
            session.ide.driver.waitForIndicators(5.minutes)
            val deadline = System.nanoTime() + SOAK.inWholeNanoseconds
            while (System.nanoTime() < deadline) {
                session.guard(Duration.ZERO)
                Thread.sleep(POLL_MS)
            }
            screenshotLines = session.screenshotLogLines()
            println("SOAK idea.log lines mentioning screenshot after ${SOAK.inWholeSeconds}s: ${screenshotLines.size}")
        } finally {
            session.close()
        }
        assertTrue(screenshotLines.isEmpty(), "idea.log mentions screenshots:\n${screenshotLines.joinToString("\n")}")
    }

    @Test
    fun `a failing driver call tries to screenshot and is blocked`() {
        assumeTrue(System.getenv("CLAUDEX_SOAK") == "1", "set CLAUDEX_SOAK=1 to fail a driver call on purpose and prove no screenshot is taken")
        val context = newPhpStormContext("captureDriverError")
        val session = GuardedIde.start(context)
        var screenshotLines = emptyList<String>()
        var failure: Throwable? = null
        var direct: Throwable? = null
        try {
            failure = runCatching { session.ide.driver.withContext { error("deliberate driver failure") } }.exceptionOrNull()
            direct = runCatching { session.ide.driver.takeScreenshot("direct") }.exceptionOrNull()
            screenshotLines = session.screenshotLogLines()
            println("DRIVER_ERROR blocked takeScreenshot requests: ${session.blockedScreenshots.get()}, failure: ${failure?.javaClass?.simpleName}")
        } finally {
            session.close()
        }
        assertNotNull(failure, "the deliberate driver failure did not surface")
        assertTrue(session.blockedScreenshots.get() >= 2, "expected the driverError and the direct request to reach the block, saw ${session.blockedScreenshots.get()}")
        assertNotNull(direct, "a direct Driver.takeScreenshot must be refused")
        assertTrue(screenshotLines.isEmpty(), "idea.log mentions screenshots:\n${screenshotLines.joinToString("\n")}")
    }

    private companion object {
        val SOAK = 80.seconds
        const val POLL_MS = 5_000L
    }
}
