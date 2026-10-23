package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.waitForIndicators
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhpStormNextEditUiTest {
    private lateinit var session: GuardedIde
    private lateinit var scenario: NextEditScenario

    @BeforeAll
    fun startIde() {
        assumeTrue(System.getenv("CLAUDE_IT") == "1", "set CLAUDE_IT=1 to run PhpStorm against the real claude CLI")
        val context = newPhpStormContext("nextEditScenarios", nextEdit = true)
        val original = Path(System.getProperty("ui.test.project")).resolve(CONTROLLER).readText()
        session = GuardedIde.start(context)
        scenario = NextEditScenario(session.ide, session.log, original)
        with(session.ide.driver) {
            waitForIndicators(5.minutes)
            openFile(CONTROLLER)
        }
        session.guard(Duration.ZERO)
    }

    @AfterAll
    fun stopIde() {
        if (::session.isInitialized) session.close()
    }

    @Test
    fun `renaming a parameter suggests the next use, Tab jumps then applies`() = scenario.verify()
}
