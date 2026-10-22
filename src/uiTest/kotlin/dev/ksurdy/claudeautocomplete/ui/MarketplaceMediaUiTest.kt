package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MarketplaceMediaUiTest {
    private val wanted: Set<String> = System.getenv("CLAUDEX_MEDIA_ONLY")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    private val outDir: Path = Path(System.getenv("CLAUDEX_MEDIA_OUT") ?: DEFAULT_OUT)

    @Test
    fun `completion media`() = session("mediaCompletion", nextEdit = false) { stage ->
        val scenes = CompletionScenes(stage)
        mapOf(
            "whole-body" to scenes::wholeBody,
            "whole-method" to scenes::wholeMethod,
            "mid-line" to scenes::midLine,
            "alternatives" to scenes::alternatives,
        )
    }

    @Test
    fun `next edit media`() = session("mediaNextEdit", nextEdit = true) { stage ->
        val scenes = NextEditAndSettingsScenes(stage)
        mapOf("next-edit" to scenes::nextEdit, "settings" to scenes::settings)
    }

    private fun session(name: String, nextEdit: Boolean, scenes: (MediaStage) -> Map<String, () -> String?>) {
        assumeTrue(System.getenv("CLAUDE_IT") == "1" && System.getenv("CLAUDEX_MEDIA") == "1", "set CLAUDE_IT=1 and CLAUDEX_MEDIA=1 to produce Marketplace media")
        val work = Files.createTempDirectory("claudex-media-")
        val context = newPhpStormContext(name, nextEdit, PROJECT_NAME)
        seedLook(context.paths.configDir)
        seedProject(context.resolvedProjectHome)
        val original = Path(System.getProperty("ui.test.project")).resolve(CONTROLLER).readText()
        val ide = context.runIdeWithDriver(runTimeout = RUN_TIMEOUT)
        val problems = mutableListOf<String>()
        try {
            val guarded = ScreenshotGuard.cancelPeriodicScreenshots(ide.driver, GUARD_TIMEOUT)
            check(guarded > 0) { "could not cancel the IDE's periodic OS screenshot job, aborting" }
            println("MEDIA cancelled $guarded periodic screenshot job(s)")
            val log = PluginLog(context.paths.testHome.resolve("log/idea.log"))
            val stage = MediaStage(ide, log, original, outDir.createDirectories(), work)
            val selected = scenes(stage).filter { wanted.isEmpty() || it.key in wanted }
            if (selected.isEmpty()) return
            with(ide.driver) {
                waitForIndicators(5.minutes)
                openFile(CONTROLLER)
            }
            stage.prepare()
            selected.forEach { (id, scene) ->
                ScreenshotGuard.cancelPeriodicScreenshots(ide.driver, Duration.ZERO)
                val problem = scene()
                println("MEDIA RESULT $id: ${problem ?: "ok"}")
                if (problem != null) problems += "$id: $problem"
            }
        } finally {
            ide.closeIdeAndWait(2.minutes, takeScreenshot = false)
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun seedLook(configDir: Path) {
        val options = configDir.resolve("options").createDirectories()
        options.resolve("laf.xml").writeText(LAF)
        options.resolve("colors.scheme.xml").writeText(COLOR_SCHEME)
        options.resolve("editor.xml").writeText(EDITOR)
        options.resolve("editor.codeinsight.xml").writeText(CODE_INSIGHT)
    }

    private fun seedProject(home: Path) {
        val idea = home.resolve(".idea").createDirectories()
        idea.resolve("php.xml").writeText(PHP_SETTINGS)
    }

    private companion object {
        const val DEFAULT_OUT = "docs/marketplace"
        const val PROJECT_NAME = "admin-portal"
        val GUARD_TIMEOUT = 45.seconds
        val RUN_TIMEOUT = 90.minutes
        const val PHP_SETTINGS = """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="PhpIncludePathManager"><include_path><path value="${'$'}PROJECT_DIR${'$'}/vendor-stubs" /></include_path></component>
  <component name="PhpProjectSharedConfiguration" php_language_level="8.3" />
</project>"""
        const val LAF = """<application><component name="LafManager" autodetect="false"><laf themeId="Darcula" /></component></application>"""
        const val COLOR_SCHEME = """<application><component name="EditorColorsManagerImpl"><global_color_scheme name="Darcula" /></component></application>"""
        const val EDITOR = """<application><component name="EditorSettingsExternalizable"><option name="IS_CARET_BLINKING" value="false" /></component></application>"""
        const val CODE_INSIGHT = """<application><component name="CodeInsightSettings"><option name="AUTO_POPUP_COMPLETION_LOOKUP" value="false" /></component></application>"""
    }
}
