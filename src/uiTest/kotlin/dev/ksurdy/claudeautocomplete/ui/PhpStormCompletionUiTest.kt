package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PhpStormCompletionUiTest {
    private class Observation(val logLine: String?, val body: String, val fileAfterAccept: String)

    @Test
    fun `empty controller action gets a complete multi line body`() {
        assumeTrue(System.getenv("CLAUDE_IT") == "1", "set CLAUDE_IT=1 to run PhpStorm against the real claude CLI")
        val context = newContext("emptyControllerAction")
        val log = context.paths.testHome.resolve("log/idea.log")
        var observation: Observation? = null
        val run = context.runIdeWithDriver()
        try {
            with(run.driver) {
                waitForIndicators(5.minutes)
                openFile(CONTROLLER)
                ideFrame {
                    codeEditor {
                        val anchor = text.indexOf(EMPTY_ACTION_ANCHOR)
                        check(anchor >= 0) { "fixture anchor not found in $CONTROLLER" }
                        moveCaretToOffset(anchor + EMPTY_ACTION_ANCHOR.length)
                        observation = typeEnterAndAccept(this@with, log)
                    }
                }
            }
        } finally {
            run.closeIdeAndWait(IDE_CLOSE_TIMEOUT, takeScreenshot = false)
        }
        val seen = observation ?: fail("IDE closed before a suggestion was observed\n${pluginLog(log)}")
        val report = "body=<<\n${seen.body.replace(" ", "·")}>>\nlog:\n${pluginLog(log)}"
        println("UI result\n$report")

        val lines = seen.body.lines().filter { it.isNotBlank() }
        assertTrue(seen.logLine?.contains(COMPLETION_RECEIVED) == true, "the plugin did not receive a completion\n$report")
        assertTrue(lines.size >= MIN_BODY_LINES, "expected a body of at least $MIN_BODY_LINES lines\n$report")
        assertTrue(!lines.first().removePrefix(BODY_INDENT).first().isWhitespace(), "first line is indented past the body\n$report")
        assertTrue(lines.all { it.startsWith(BODY_INDENT) }, "body lines lost the body indent\n$report")
        assertPhpLints(seen.fileAfterAccept, report)
    }

    private fun JEditorUiComponent.typeEnterAndAccept(driver: Driver, log: Path): Observation {
        driver.invokeAction(ENTER_ACTION, true, component)
        val logLine = waitForCompletionLog(log)
        Thread.sleep(RENDER_SETTLE_MS)
        driver.invokeAction(ACCEPT_ACTION, true, component)
        Thread.sleep(RENDER_SETTLE_MS)
        val after = text
        return Observation(logLine, after.substringAfter(EMPTY_ACTION_ANCHOR + "\n").substringBeforeLast(METHOD_END), after)
    }

    private fun waitForCompletionLog(log: Path): String? {
        val deadline = System.nanoTime() + SUGGESTION_TIMEOUT.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            val line = pluginLines(log).lastOrNull { COMPLETION_RECEIVED in it || COMPLETION_FAILED in it }
            if (line != null) return line
            Thread.sleep(POLL_MS)
        }
        return null
    }

    @OptIn(ExperimentalPathApi::class)
    private fun newContext(name: String): IDETestContext {
        val project = Files.createTempDirectory("claudex-ui-")
        Path(System.getProperty("ui.test.project")).copyToRecursively(project, followLinks = false, overwrite = true)
        val testCase = TestCase(IdeProductProvider.PS, LocalProjectInfo(project)).let { case ->
            System.getProperty("ui.test.ide.version")?.let(case::useRelease) ?: case
        }
        return Starter.newContext(name, testCase).apply {
            paths.pluginsDir.deleteRecursively()
            paths.testHome.resolve("log").deleteRecursively()
            PluginConfigurator(this)
                .installPluginFromPath(Path(System.getProperty("path.to.build.plugin")))
                .disablePlugins(FULL_LINE_PLUGIN)
            seedSettings(paths.configDir)
            applyVMOptionsPatch { addSystemProperty("idea.log.debug.categories", LOG_CATEGORY) }
        }
    }

    private fun seedSettings(configDir: Path) {
        val options = configDir.resolve("options").createDirectories()
        options.resolve("claude-autocomplete.xml").writeText(
            """
            <application>
              <component name="ClaudeAutocompleteSettings">
                <option name="consentGiven" value="true" />
              </component>
            </application>
            """.trimIndent(),
        )
    }

    private fun pluginLines(log: Path): List<String> =
        if (log.exists()) log.readLines().filter { PLUGIN_LOGGER in it } else emptyList()

    private fun pluginLog(log: Path): String = pluginLines(log).joinToString("\n").ifEmpty { "(no plugin log lines)" }

    private fun assertPhpLints(source: String, report: String) {
        val file = Files.createTempFile("claudex-ui-", ".php")
        file.writeText(source)
        val process = ProcessBuilder("php", "-l", file.toString()).redirectErrorStream(true).start()
        check(process.waitFor(PHP_LINT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "php -l timed out" }
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.exitValue() == 0, "accepted code does not parse: $output\n$report")
    }

    private companion object {
        const val CONTROLLER = "src/Controller/Admin/LearningTeamController.php"
        const val EMPTY_ACTION_ANCHOR = "StudentLearningTeamResolver \$studentLearningTeamResolver\n    ): Response {"
        const val BODY_INDENT = "        "
        const val MIN_BODY_LINES = 3
        const val LOG_CATEGORY = "#dev.ksurdy.claudeautocomplete"
        const val PLUGIN_LOGGER = "#dev.ksurdy.claudeautocomplete."
        const val COMPLETION_RECEIVED = "Completion received"
        const val COMPLETION_FAILED = "Completion failed"
        const val ENTER_ACTION = "EditorEnter"
        const val ACCEPT_ACTION = "InsertInlineCompletionAction"
        const val FULL_LINE_PLUGIN = "org.jetbrains.completion.full.line"
        const val METHOD_END = "\n    }\n}"
        const val POLL_MS = 250L
        const val RENDER_SETTLE_MS = 700L
        const val PHP_LINT_TIMEOUT_SECONDS = 30L
        val SUGGESTION_TIMEOUT = 40.seconds
        val IDE_CLOSE_TIMEOUT = 2.minutes
    }
}
