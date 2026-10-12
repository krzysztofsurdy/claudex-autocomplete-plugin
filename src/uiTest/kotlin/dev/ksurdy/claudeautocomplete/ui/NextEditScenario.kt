package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.ide.starter.driver.engine.BackgroundRun
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NextEditScenario(private val ide: BackgroundRun, private val log: PluginLog, private val original: String) {
    private class Attempt(val shown: Boolean, val problem: String?, val summary: String)

    fun verify() {
        val history = mutableListOf<String>()
        var problem: String? = "not run"
        for (number in 1..MAX_ATTEMPTS) {
            val attempt = runAttempt()
            history += "attempt $number: ${attempt.summary}"
            problem = attempt.problem
            if (attempt.shown) break
        }
        val report = history.joinToString("\n") + "\nlog:\n" + log.dump()
        println("UI result nextEditRename\n$report")
        assertTrue(problem == null, "$problem after ${history.size} attempts\n$report")
    }

    private fun runAttempt(): Attempt {
        var attempt: Attempt? = null
        with(ide.driver) {
            ideFrame {
                codeEditor {
                    val parameter = original.indexOf(PARAMETER) + PARAMETER_PREFIX.length
                    replaceDocument(this@with, original, parameter)
                    Thread.sleep(SETTLE_MS)
                    setSelection(parameter, parameter + OLD_STEM.length)
                    NEW_STEM.forEach {
                        typeCharacter(this@with, it)
                        Thread.sleep(TYPING_PAUSE_MS)
                    }
                    val renamed = text
                    Thread.sleep(SETTLE_MS)
                    attempt = if (RENAMED_PARAMETER !in renamed) {
                        Attempt(false, "typing did not rename the parameter", "typing failed")
                    } else {
                        awaitPreview()?.let { verifyPreview(it, renamed) }
                            ?: Attempt(false, "no next edit suggestion appeared within $PREVIEW_TIMEOUT", "no suggestion")
                    }
                }
            }
        }
        return attempt!!
    }

    private fun JEditorUiComponent.awaitPreview(): NextEditPreview? {
        val deadline = System.nanoTime() + PREVIEW_TIMEOUT.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            nextEditPreview(ide.driver)?.let { return it }
            Thread.sleep(POLL_MS)
        }
        return null
    }

    private fun JEditorUiComponent.verifyPreview(preview: NextEditPreview, renamed: String): Attempt {
        val caretLine = lineOf(caretOffset())
        val summary = "shown at line ${preview.line + 1}, caret line ${caretLine + 1}, hint=${preview.hint}, replacement=<<\n${preview.text}>>"
        fun failed(reason: String) = Attempt(true, reason, summary)
        proseProblem(preview.text)?.let { return failed(it) }
        if (preview.line == caretLine) return failed("suggestion is on the caret line")
        if (RENAMED_NAME !in preview.text) return failed("replacement does not use the new name")
        if (OLD_NAME in preview.text) return failed("replacement still has the old name")

        ide.driver.invokeAction(TAB_ACTION, true, component)
        Thread.sleep(SETTLE_MS)
        val jumpOffset = caretOffset()
        val jumpLine = lineOf(jumpOffset)
        if (text != renamed) return failed("the first Tab changed the text")
        if (jumpLine == caretLine) return failed("the first Tab did not move the caret off the signature")
        if (jumpLine > preview.line) return failed("the first Tab jumped below the suggestion")
        val applyHint = nextEditPreview(ide.driver)?.hint
        if (applyHint == null || applyHint == preview.hint) return failed("the hint did not change after the jump: $applyHint")

        ide.driver.invokeAction(TAB_ACTION, true, component)
        Thread.sleep(SETTLE_MS)
        val applied = text
        val lines = applied.lines()
        val changed = lines.subList(jumpLine, jumpLine + preview.lines.size).joinToString("\n")
        if (changed != preview.text) return failed("applied text differs from the preview: <<\n$changed>>")
        if (applied.count(OLD_NAME) >= renamed.count(OLD_NAME)) return failed("the old name was not replaced")
        if (caretOffset() != jumpOffset + preview.text.length) return failed("the caret is not at the end of the new text")
        if (nextEditPreview(ide.driver) != null) return failed("the suggestion is still shown after applying")
        return Attempt(true, phpLintError(applied), "$summary\napplied lines ${jumpLine + 1}..${jumpLine + preview.lines.size}")
    }

    private fun String.count(word: String): Int = split(word).size - 1

    private companion object {
        const val PARAMETER_PREFIX = "int \$"
        const val PARAMETER = PARAMETER_PREFIX + "teamId\n"
        const val OLD_STEM = "team"
        const val NEW_STEM = "group"
        const val OLD_NAME = "teamId"
        const val RENAMED_NAME = "groupId"
        const val RENAMED_PARAMETER = "int \$groupId"
        const val TAB_ACTION = "EditorTab"
        const val MAX_ATTEMPTS = 2
        const val SETTLE_MS = 1000L
        const val TYPING_PAUSE_MS = 80L
        const val POLL_MS = 500L
        val PREVIEW_TIMEOUT = 60.seconds
    }
}
