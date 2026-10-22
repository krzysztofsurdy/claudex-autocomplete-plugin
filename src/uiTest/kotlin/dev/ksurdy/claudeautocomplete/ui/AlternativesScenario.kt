package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.ide.starter.driver.engine.BackgroundRun
import kotlin.test.assertTrue

class AlternativesScenario(
    private val ide: BackgroundRun,
    private val log: PluginLog,
    private val original: String,
    private val runner: ScenarioRunner,
) {
    private class Attempt(val shown: Boolean, val problem: String?, val summary: String)

    fun verify(seed: Seed, trigger: (JEditorUiComponent, Driver) -> Unit) {
        val history = mutableListOf<String>()
        var problem: String? = "not run"
        for (number in 1..MAX_ATTEMPTS) {
            val attempt = runAttempt(seed, trigger)
            history += "attempt $number: ${attempt.summary}"
            problem = attempt.problem
            if (attempt.shown) break
        }
        val report = history.joinToString("\n")
        println("UI result alternatives\n$report")
        assertTrue(problem == null, "$problem after ${history.size} attempts\n$report")
    }

    private fun runAttempt(seed: Seed, trigger: (JEditorUiComponent, Driver) -> Unit): Attempt {
        var attempt: Attempt? = null
        with(ide.driver) {
            ideFrame {
                codeEditor {
                    val (seeded, caret) = seed.apply(original)
                    replaceDocument(this@with, seeded, caret)
                    runner.awaitQuiet()
                    val firstFinishedBefore = log.finished().size
                    val firstLinesBefore = log.lines().size
                    trigger(this, this@with)
                    val firstLast = runner.awaitCompletion(firstFinishedBefore)
                    Thread.sleep(ScenarioRunner.RENDER_SETTLE_MS)
                    val firstLog = log.lines().drop(firstLinesBefore)
                    val firstOutcome = outcomeOf(firstLast, firstLog)
                    if (firstOutcome != Outcome.Received) {
                        attempt = Attempt(false, "first suggestion not shown: $firstOutcome", "first request $firstOutcome\n${firstLog.joinToString("\n")}")
                        return@codeEditor
                    }
                    val first = shownText()
                    val secondFinishedBefore = log.finished().size
                    val requestsBefore = log.requested()
                    val secondLinesBefore = log.lines().size
                    ide.driver.invokeAction(NEXT_ACTION, true, component)
                    val secondLast = runner.awaitCompletion(secondFinishedBefore)
                    Thread.sleep(ScenarioRunner.RENDER_SETTLE_MS)
                    val secondLog = log.lines().drop(secondLinesBefore)
                    val secondOutcome = outcomeOf(secondLast, secondLog)
                    val summary = "first=<<$first>> secondOutcome=$secondOutcome\nfirst log:\n${firstLog.joinToString("\n")}\nsecond log:\n${secondLog.joinToString("\n")}"
                    if (secondOutcome != Outcome.Received) {
                        attempt = Attempt(false, "no second suggestion: $secondOutcome", summary)
                        return@codeEditor
                    }
                    attempt = verifySecond(first, requestsBefore, summary)
                }
            }
        }
        return attempt!!
    }

    private fun JEditorUiComponent.verifySecond(first: String, requestsBefore: Int, summary: String): Attempt {
        fun failed(reason: String) = Attempt(true, reason, summary)
        if (log.requested() != requestsBefore + 1) return failed("Next did not send exactly one new request")
        val second = shownText()
        val before = text
        val caretOffset = interact { getCaretModel().getOffset() }
        ide.driver.invokeAction(ScenarioRunner.ACCEPT_ACTION, true, component)
        Thread.sleep(ScenarioRunner.RENDER_SETTLE_MS)
        val after = text
        val inserted = after.substring(caretOffset, caretOffset + after.length - before.length)
        val problem = when {
            first.isBlank() -> "could not read the first variant"
            inserted.isBlank() -> "accepting inserted nothing"
            inserted.trim() == first.trim() -> "the accepted variant equals the first one"
            second.isNotBlank() && second.trim() != inserted.trim() -> "the shown variant <<$second>> differs from the accepted one"
            else -> proseProblem(inserted) ?: phpLintError(after)
        }
        return Attempt(true, problem, "$summary\nsecond=<<$second>> inserted=<<$inserted>>")
    }

    private fun JEditorUiComponent.shownText(): String {
        val hints = getInlineCompletion().joinToString("") { it.text }
        val trailing = getAfterLineHints(getCaretLine()).joinToString("")
        return hints + trailing
    }

    private companion object {
        const val NEXT_ACTION = "NextInlineCompletionSuggestionAction"
        const val MAX_ATTEMPTS = 2
    }
}
