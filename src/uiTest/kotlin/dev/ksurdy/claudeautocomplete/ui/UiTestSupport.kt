package dev.ksurdy.claudeautocomplete.ui

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
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

const val CONTROLLER = "src/Controller/Admin/LearningTeamController.php"
const val CARET = "<caret>"
const val COMPLETION_RECEIVED = "Completion received"
const val COMPLETION_FAILED = "Completion failed"
const val COMPLETION_REQUESTED = "Requesting completion"
const val COMPLETION_EMPTY = "Completion empty"
const val COMPLETION_EMPTIED = "Completion emptied"
const val COMPLETION_CANCELLED = "Completion cancelled"
const val ALTERNATIVE_REQUESTED = "Requesting alternative"
const val ALTERNATIVE_RECEIVED = "Alternative received"
const val ALTERNATIVE_DROPPED = "Alternative dropped"
const val ALTERNATIVE_CANCELLED = "Alternative cancelled"

private val TERMINAL_MARKERS = listOf(
    COMPLETION_RECEIVED, COMPLETION_FAILED, COMPLETION_EMPTY, COMPLETION_CANCELLED,
    ALTERNATIVE_RECEIVED, ALTERNATIVE_DROPPED, ALTERNATIVE_CANCELLED,
)
private const val LOG_CATEGORY = "#dev.ksurdy.claudeautocomplete,#com.intellij.codeInsight.inline.completion"
private const val PLUGIN_LOGGER = "#dev.ksurdy.claudeautocomplete."
private const val FULL_LINE_PLUGIN = "org.jetbrains.completion.full.line"
private const val PHP_LINT_TIMEOUT_SECONDS = 30L

class PluginLog(private val file: Path) {
    fun lines(): List<String> = if (file.exists()) file.readLines().filter { PLUGIN_LOGGER in it } else emptyList()

    fun finished(): List<String> = lines().filter { line -> TERMINAL_MARKERS.any { it in line } }

    fun requested(): Int = lines().count { COMPLETION_REQUESTED in it || (ALTERNATIVE_REQUESTED in it && "retry=false" in it) }

    fun dump(): String = lines().joinToString("\n").ifEmpty { "(no plugin log lines)" }
}

class Seed(val anchor: String, val replacement: String) {
    fun apply(original: String): Pair<String, Int> {
        check(original.split(anchor).size == 2) { "seed anchor must occur exactly once: $anchor" }
        val seeded = original.replace(anchor, replacement)
        val caret = seeded.indexOf(CARET)
        check(caret >= 0) { "seed replacement has no $CARET marker" }
        return seeded.replace(CARET, "") to caret
    }
}

@OptIn(ExperimentalPathApi::class)
fun newPhpStormContext(name: String, nextEdit: Boolean = false): IDETestContext {
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
        seedSettings(paths.configDir, nextEdit)
        applyVMOptionsPatch { addSystemProperty("idea.log.debug.categories", LOG_CATEGORY) }
    }
}

private fun seedSettings(configDir: Path, nextEdit: Boolean) {
    val options = configDir.resolve("options").createDirectories()
    options.resolve("claude-autocomplete.xml").writeText(
        """
        <application>
          <component name="ClaudeAutocompleteSettings">
            <option name="consentGiven" value="true" />
            <option name="nextEditSuggestions" value="$nextEdit" />
          </component>
        </application>
        """.trimIndent(),
    )
}

fun phpLintError(source: String): String? {
    val file = Files.createTempFile("claudex-ui-", ".php")
    file.writeText(source)
    val process = ProcessBuilder("php", "-l", file.toString()).redirectErrorStream(true).start()
    check(process.waitFor(PHP_LINT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "php -l timed out" }
    val output = process.inputStream.bufferedReader().readText()
    return if (process.exitValue() == 0) null else "accepted code does not parse: $output"
}
