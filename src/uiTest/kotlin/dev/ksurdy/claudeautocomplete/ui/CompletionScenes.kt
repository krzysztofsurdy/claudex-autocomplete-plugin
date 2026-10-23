package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

class CompletionScenes(private val stage: MediaStage) {
    private val original = stage.original
    private val log = stage.log
    private val runner = stage.runner

    fun wholeBody(): String? = retrying { stage.record("whole-body") { editor, recorder -> wholeBodyScene(editor, recorder) } }

    private fun wholeBodyScene(editor: JEditorUiComponent, recorder: FrameRecorder): String? {
        val (seeded, caret) = Seed(EMPTY_ACTION_ANCHOR, EMPTY_ACTION_ANCHOR + CARET).apply(original)
        stage.seed(editor, seeded, caret, BODY_ROW)
        recorder.start()
        Thread.sleep(LEAD_IN_MS)
        val finishedBefore = log.finished().size
        val linesBefore = log.lines().size
        runner.enter(editor)
        return shownProblem(editor, linesBefore) { runner.awaitCompletion(finishedBefore) } ?: acceptProblem(editor, MIN_BODY_LINES)
    }

    fun wholeMethod(): String? = retrying { stage.record("whole-method") { editor, recorder -> wholeMethodScene(editor, recorder) } }

    private fun wholeMethodScene(editor: JEditorUiComponent, recorder: FrameRecorder): String? {
        val (seeded, caret) = Seed(BETWEEN_METHODS, "    }\n\n    $CARET\n\n    #[Route('/admin/learning-team/replace-course'").apply(original)
        stage.seed(editor, seeded, caret, METHOD_ROW)
        recorder.start()
        Thread.sleep(LEAD_IN_MS)
        stage.type(editor, METHOD_HEAD, METHOD_TYPING_MS)
        val requestedBefore = log.requested()
        val linesBefore = log.lines().size
        editor.typeCharacter(stage.driver, METHOD_OPEN)
        if (!awaitRequest(requestedBefore)) return "typing the parenthesis did not request a suggestion"
        val last = runner.awaitCompletion(log.finished().size)
        return shownProblem(editor, linesBefore) { last } ?: acceptProblem(editor, MIN_METHOD_LINES)
    }

    private fun awaitRequest(before: Int): Boolean {
        val deadline = System.nanoTime() + REQUEST_TIMEOUT_MS * NANOS_PER_MS
        while (System.nanoTime() < deadline) {
            if (log.requested() > before) return true
            Thread.sleep(POLL_MS)
        }
        return false
    }

    fun midLine(): String? = suggestion("mid-line") { editor ->
        val seed = Seed(REDIRECT_LINE, "        return \$this->redirectToRoute(${CARET}['id' => \$id]);")
        val (seeded, caret) = seed.apply(original)
        stage.seed(editor, seeded, caret, MID_LINE_ROW)
        val finishedBefore = log.finished().size
        val linesBefore = log.lines().size
        editor.typeCharacter(stage.driver, ' ')
        shownProblem(editor, linesBefore) { runner.awaitCompletion(finishedBefore) }
    }

    fun alternatives(): String? {
        var problem: String? = null
        stage.withEditor { editor -> problem = alternativesScene(editor) }
        return problem
    }

    private fun alternativesScene(editor: JEditorUiComponent): String? {
        val seed = Seed(NOT_FOUND_ANCHOR, "throw \$this->createNotFoundException($CARET);\n        }\n\n        \$form")
        val (seeded, caret) = seed.apply(original)
        stage.seed(editor, seeded, caret, MID_LINE_ROW)
        val finishedBefore = log.finished().size
        val linesBefore = log.lines().size
        editor.typeCharacter(stage.driver, ' ')
        shownProblem(editor, linesBefore) { runner.awaitCompletion(finishedBefore) }?.let { return it }
        val first = editor.shownText()
        val firstShot = stage.snap("alternatives-first")
        val secondBefore = log.finished().size
        val secondLines = log.lines().size
        stage.driver.invokeAction(NEXT_ACTION, true, editor.component)
        shownProblem(editor, secondLines) { runner.awaitCompletion(secondBefore) }?.let { return it }
        if (editor.shownText().trim() == first.trim()) return "the alternative equals the first suggestion"
        val secondShot = stage.snap("alternatives-second")
        val target = stage.outDir.resolve("alternatives.png")
        ComparisonShot.compose(firstShot, secondShot, target)
        stage.reportSize(target)
        return null
    }

    private fun suggestion(name: String, scene: (JEditorUiComponent) -> String?): String? {
        var problem: String? = null
        stage.withEditor { editor ->
            problem = scene(editor)
            if (problem == null) stage.shot(name)
        }
        return problem
    }

    private fun retrying(scene: () -> String?): String? {
        var problem: String? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            problem = scene()
            if (problem == null) return null
            println("MEDIA attempt $attempt failed: $problem")
            runCatching { stage.withEditor { stage.driver.invokeAction(ESCAPE_ACTION, true, it.component) } }
            Thread.sleep(RESET_MS)
        }
        return problem
    }

    private fun shownProblem(editor: JEditorUiComponent, linesBefore: Int, await: () -> String?): String? {
        val last = await()
        Thread.sleep(ScenarioRunner.RENDER_SETTLE_MS)
        val outcome = outcomeOf(last, log.lines().drop(linesBefore))
        if (outcome != Outcome.Received) return "no suggestion was shown: $outcome"
        val shown = editor.shownText()
        if (shown.isBlank()) return "the suggestion is not visible"
        return proseProblem(shown)
    }

    private fun acceptProblem(editor: JEditorUiComponent, minLines: Int): String? {
        Thread.sleep(READ_HOLD_MS)
        val before = editor.text
        val accepted = stage.pressTab(editor, ScenarioRunner.ACCEPT_ACTION)
        Thread.sleep(HOLD_AFTER_ACCEPT_MS)
        val added = editor.text.lines().size - before.lines().size
        return when {
            !accepted -> "Tab did not accept the suggestion"
            added + 1 < minLines -> "the accepted text is only ${added + 1} lines"
            else -> null
        }
    }

    private companion object {
        const val EMPTY_ACTION_ANCHOR = "StudentLearningTeamResolver \$studentLearningTeamResolver\n    ): Response {"
        const val REDIRECT_LINE = "        return \$this->redirectToRoute('admin_learning_team_edit', ['id' => \$id]);"
        const val NOT_FOUND_ANCHOR = "throw \$this->createNotFoundException('Participant not found.');\n        }\n\n        \$form"
        const val BETWEEN_METHODS = "    }\n\n    #[Route('/admin/learning-team/replace-course'"
        const val METHOD_HEAD = "public function restoreAction"
        const val METHOD_OPEN = '('
        const val METHOD_TYPING_MS = 90L
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val POLL_MS = 100L
        const val NANOS_PER_MS = 1_000_000L
        const val NEXT_ACTION = "NextInlineCompletionSuggestionAction"
        const val ESCAPE_ACTION = "EditorEscape"
        const val RESET_MS = 800L
        const val BODY_ROW = 9
        const val METHOD_ROW = 6
        const val MID_LINE_ROW = 14
        const val MIN_BODY_LINES = 3
        const val MIN_METHOD_LINES = 3
        const val LEAD_IN_MS = 400L
        const val MAX_ATTEMPTS = 3
        const val HOLD_AFTER_ACCEPT_MS = 600L
        const val READ_HOLD_MS = 1800L
    }
}
