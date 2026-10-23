package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.sdk.waitForIndicators
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readLines
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
            val ideaLog = context.paths.testHome.resolve("log/idea.log")
            screenshotLines = if (ideaLog.exists()) ideaLog.readLines().filter { "screenshot" in it.lowercase() } else emptyList()
            println("SOAK idea.log lines mentioning screenshot after ${SOAK.inWholeSeconds}s: ${screenshotLines.size}")
        } finally {
            session.close()
        }
        assertTrue(screenshotLines.isEmpty(), "idea.log mentions screenshots:\n${screenshotLines.joinToString("\n")}")
        assertTrue(Files.exists(context.paths.testHome.resolve("log")), "log dir missing")
    }

    private companion object {
        val SOAK = 80.seconds
        const val POLL_MS = 5_000L
    }
}
