package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.ide.starter.driver.engine.BackgroundRun
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class Observation(
    val logLine: String?,
    val before: String,
    val after: String,
    val caretOffset: Int,
    val newLogLines: List<String>,
) {
    val received: Boolean get() = logLine?.contains(COMPLETION_RECEIVED) == true

    val inserted: String get() = after.substring(caretOffset, caretOffset + after.length - before.length)

    fun caretLineAfter(): String {
        val start = after.lastIndexOf('\n', caretOffset - 1) + 1
        val end = after.indexOf('\n', caretOffset).takeIf { it >= 0 } ?: after.length
        return after.substring(start, end)
    }

    fun report(shown: String = inserted): String =
        "inserted=<<\n${shown.replace(" ", "·")}>>\nlog:\n${newLogLines.joinToString("\n").ifEmpty { "(no plugin log lines)" }}"
}

class ScenarioRunner(private val ide: BackgroundRun, private val log: PluginLog, private val original: String) {
    fun verify(name: String, seed: Seed, trigger: (JEditorUiComponent, Driver) -> Unit, check: (Observation) -> String?) {
        val attempts = mutableListOf<Observation>()
        var problem: String? = "not run"
        while (problem != null && attempts.size < MAX_ATTEMPTS) {
            if (attempts.isNotEmpty()) Thread.sleep(CACHE_EXPIRY_MS)
            val seen = observe(seed, trigger)
            attempts += seen
            problem = if (seen.received) check(seen) else "the plugin did not receive a completion"
        }
        val report = attempts.withIndex().joinToString("\n") { (index, seen) -> "attempt ${index + 1}: ${seen.report()}" }
        println("UI result $name\n$report")
        assertTrue(problem == null, "$problem after ${attempts.size} attempts\n$report")
    }

    fun observe(seed: Seed, trigger: (JEditorUiComponent, Driver) -> Unit): Observation {
        var observation: Observation? = null
        with(ide.driver) {
            ideFrame {
                codeEditor {
                    val (seeded, caret) = seed.apply(original)
                    replaceDocument(this@with, seeded, caret)
                    awaitQuiet()
                    val finishedBefore = log.finished().size
                    val linesBefore = log.lines().size
                    trigger(this, this@with)
                    val logLine = awaitCompletion(finishedBefore)
                    Thread.sleep(RENDER_SETTLE_MS)
                    val before = text
                    val caretOffset = interact { getCaretModel().getOffset() }
                    if (logLine?.contains(COMPLETION_RECEIVED) == true) {
                        invokeAction(ACCEPT_ACTION, true, component)
                        Thread.sleep(RENDER_SETTLE_MS)
                    }
                    observation = Observation(logLine, before, text, caretOffset, log.lines().drop(linesBefore))
                }
            }
        }
        return observation ?: fail("no observation\n${log.dump()}")
    }

    fun enter(editor: JEditorUiComponent) = ide.driver.invokeAction(ENTER_ACTION, true, editor.component)

    private fun awaitQuiet() {
        Thread.sleep(QUIET_SETTLE_MS)
        val deadline = System.nanoTime() + QUIET_TIMEOUT.inWholeNanoseconds
        while (System.nanoTime() < deadline && log.requested() > log.finished().size) Thread.sleep(POLL_MS)
        Thread.sleep(QUIET_SETTLE_MS)
    }

    private fun awaitCompletion(finishedBefore: Int): String? {
        val deadline = System.nanoTime() + SUGGESTION_TIMEOUT.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            val finished = log.finished()
            if (finished.size > finishedBefore) return finished.last()
            Thread.sleep(POLL_MS)
        }
        return null
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val CACHE_EXPIRY_MS = 61_000L
        const val ENTER_ACTION = "EditorEnter"
        const val ACCEPT_ACTION = "InsertInlineCompletionAction"
        const val POLL_MS = 250L
        const val RENDER_SETTLE_MS = 700L
        const val QUIET_SETTLE_MS = 1000L
        val SUGGESTION_TIMEOUT = 40.seconds
        val QUIET_TIMEOUT = 20.seconds
    }
}
