package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.ide.starter.driver.engine.BackgroundRun
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class MediaStage(
    val ide: BackgroundRun,
    val log: PluginLog,
    val original: String,
    val outDir: Path,
    private val workDir: Path,
) {
    val driver: Driver get() = ide.driver
    val runner = ScenarioRunner(ide, log, original)
    lateinit var canvas: IdeCanvas
        private set

    fun prepare() {
        MediaLook.apply(driver)
        driver.ideFrame {
            resize(FRAME_WIDTH, FRAME_HEIGHT)
            Thread.sleep(LAYOUT_MS)
            canvas = IdeCanvas(driver, component)
        }
        withEditor { driver.invokeAction(HIDE_TOOL_WINDOWS, true, it.component) }
        clearNotifications()
        Thread.sleep(LAYOUT_MS)
    }

    fun clearNotifications() {
        withEditor { driver.invokeAction(CLEAR_NOTIFICATIONS, true, it.component) }
        Thread.sleep(NOTIFICATION_MS)
    }

    fun withEditor(block: (JEditorUiComponent) -> Unit) {
        driver.ideFrame { codeEditor { block(this) } }
    }

    fun seed(editor: JEditorUiComponent, text: String, caret: Int, row: Int) {
        editor.replaceDocument(driver, text, caret)
        Thread.sleep(LAYOUT_MS)
        editor.scrollOffsetToRow(driver, caret, row)
        runner.awaitQuiet()
    }

    fun type(editor: JEditorUiComponent, text: String, pauseMs: Long = TYPING_PAUSE_MS) {
        text.forEach {
            editor.typeCharacter(driver, it)
            Thread.sleep(pauseMs)
        }
    }

    fun record(name: String, scene: (JEditorUiComponent, FrameRecorder) -> String?): String? {
        var problem: String? = null
        var frames: List<RecordedFrame> = emptyList()
        withEditor { editor ->
            val recorder = FrameRecorder(canvas, Files.createTempDirectory(workDir, "$name-"))
            try {
                problem = scene(editor, recorder)
            } finally {
                frames = recorder.stop()
            }
        }
        if (frames.size < MIN_FRAMES) return "only ${frames.size} frames were captured"
        val clip = ClipTimeline().select(frames)
        GifWriter.write(clip, outDir.resolve("$name.gif"))
        println("MEDIA $name: ${frames.size} frames captured, ${clip.size} kept, problem=$problem")
        return problem
    }

    fun shot(name: String) {
        val target = outDir.resolve("$name.png")
        Files.createDirectories(outDir)
        canvas.paint(target, "png")
        reportSize(target)
    }

    fun reportSize(target: Path) {
        val image: BufferedImage = ImageIO.read(target.toFile())
        println("MEDIA ${target.fileName}: ${image.width}x${image.height}")
    }

    fun pressTab(editor: JEditorUiComponent, action: String = TAB_ACTION): Boolean {
        val before = editor.text
        driver.invokeAction(action, true, editor.component)
        Thread.sleep(ScenarioRunner.RENDER_SETTLE_MS)
        return editor.text != before
    }

    companion object {
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 800
        const val TAB_ACTION = "EditorTab"
        private const val HIDE_TOOL_WINDOWS = "HideAllWindows"
        private const val CLEAR_NOTIFICATIONS = "ClearAllNotifications"
        private const val NOTIFICATION_MS = 800L
        private const val LAYOUT_MS = 1500L
        private const val TYPING_PAUSE_MS = 70L
        private const val MIN_FRAMES = 4
    }
}
