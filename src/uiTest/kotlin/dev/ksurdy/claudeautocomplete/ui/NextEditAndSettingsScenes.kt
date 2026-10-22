package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import kotlin.time.Duration.Companion.seconds

class NextEditAndSettingsScenes(private val stage: MediaStage) {
    private val original = stage.original

    fun nextEdit(): String? = stage.record("next-edit") { editor, recorder ->
        val parameter = original.indexOf(PARAMETER) + PARAMETER_PREFIX.length
        stage.seed(editor, original, parameter, SIGNATURE_ROW)
        recorder.start()
        Thread.sleep(LEAD_IN_MS)
        editor.setSelection(parameter, parameter + OLD_STEM.length)
        stage.type(editor, NEW_STEM, TYPING_PAUSE_MS)
        val renamed = editor.text
        if (RENAMED_PARAMETER !in renamed) return@record "typing did not rename the parameter"
        val preview = awaitPreview(editor) ?: return@record "no next edit suggestion appeared"
        Thread.sleep(PREVIEW_HOLD_MS)
        val caretLine = editor.lineOf(editor.caretOffset())
        if (preview.line == caretLine) return@record "the suggestion is on the caret line"
        proseProblem(preview.text)?.let { return@record it }
        if (stage.pressTab(editor)) return@record "the first Tab changed the text"
        Thread.sleep(JUMP_HOLD_MS)
        if (!stage.pressTab(editor)) return@record "the second Tab did not apply the suggestion"
        Thread.sleep(APPLIED_HOLD_MS)
        if (editor.text.count(OLD_NAME) >= renamed.count(OLD_NAME)) "the old name was not replaced" else null
    }

    fun settings(): String? {
        val target = stage.outDir.resolve("settings.png")
        stage.canvas.paintSettingsPage(target, MediaStage.FRAME_WIDTH, MediaStage.FRAME_HEIGHT)
        stage.reportSize(target)
        return null
    }

    private fun awaitPreview(editor: JEditorUiComponent): NextEditPreview? {
        val deadline = System.nanoTime() + PREVIEW_TIMEOUT.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            editor.nextEditPreview(stage.driver)?.let { return it }
            Thread.sleep(POLL_MS)
        }
        return null
    }

    private fun String.count(word: String): Int = split(word).size - 1

    private companion object {
        const val PARAMETER_PREFIX = "int \$"
        const val PARAMETER = PARAMETER_PREFIX + "teamId\n"
        const val OLD_STEM = "team"
        const val NEW_STEM = "group"
        const val OLD_NAME = "teamId"
        const val RENAMED_PARAMETER = "int \$groupId"
        const val SIGNATURE_ROW = 3
        const val LEAD_IN_MS = 600L
        const val TYPING_PAUSE_MS = 220L
        const val PREVIEW_HOLD_MS = 1800L
        const val JUMP_HOLD_MS = 1500L
        const val APPLIED_HOLD_MS = 800L
        const val POLL_MS = 300L
        val PREVIEW_TIMEOUT = 60.seconds
    }
}
