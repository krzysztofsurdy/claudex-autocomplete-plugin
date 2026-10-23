package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.waitForIndicators
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PhpStormCompletionUiTest {
    private lateinit var session: GuardedIde
    private lateinit var scenarios: ScenarioRunner
    private lateinit var alternatives: AlternativesScenario

    @BeforeAll
    fun startIde() {
        assumeTrue(System.getenv("CLAUDE_IT") == "1", "set CLAUDE_IT=1 to run PhpStorm against the real claude CLI")
        val context = newPhpStormContext("completionScenarios")
        val original = Path(System.getProperty("ui.test.project")).resolve(CONTROLLER).readText()
        session = GuardedIde.start(context)
        scenarios = ScenarioRunner(session.ide, session.log, original)
        alternatives = AlternativesScenario(session.ide, session.log, original, scenarios)
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
    @Order(1)
    fun `empty controller action gets a complete multi line body`() {
        val seed = Seed(EMPTY_ACTION_ANCHOR, EMPTY_ACTION_ANCHOR + CARET)
        scenarios.verify("emptyControllerAction", seed, { editor, _ -> scenarios.enter(editor) }) { seen ->
            val body = seen.after.substringAfter(EMPTY_ACTION_ANCHOR + "\n").substringBeforeLast(METHOD_END)
            val lines = body.lines().filter { it.isNotBlank() }
            if (lines.size < MIN_BODY_LINES) return@verify "expected a body of at least $MIN_BODY_LINES lines"
            if (lines.first().removePrefix(BODY_INDENT).first().isWhitespace()) return@verify "first line is indented past the body"
            if (!lines.all { it.startsWith(BODY_INDENT) }) return@verify "body lines lost the body indent"
            phpLintError(seen.after)
        }
    }

    @Test
    @Order(2)
    fun `mid line suggestion inside a call stays on one line`() {
        val seed = Seed(REDIRECT_LINE, "        return \$this->redirectToRoute(${CARET}['id' => \$id]);")
        scenarios.verify("midLineInsideCall", seed, { editor, driver -> editor.typeCharacter(driver, ' ') }) { seen ->
            if (seen.inserted.isBlank()) return@verify "nothing was inserted"
            if ('\n' in seen.inserted) return@verify "mid-line insertion spans several lines"
            if (seen.before.lines().size != seen.after.lines().size) return@verify "line count changed"
            if (!seen.caretLineAfter().endsWith("['id' => \$id]);")) return@verify "the code right of the caret was lost"
            phpLintError(seen.after)
        }
    }

    @Test
    @Order(3)
    fun `member access completes with a real repository method on one line`() {
        val seed = Seed(FIND_LINE, "        \$participant = \$learningTeamParticipantRepository-$CARET")
        scenarios.verify("memberAccess", seed, { editor, driver -> editor.typeCharacter(driver, '>') }) { seen ->
            if (seen.inserted.isBlank()) return@verify "nothing was inserted"
            if ('\n' in seen.inserted) return@verify "member access suggestion spans several lines"
            if (seen.before.lines().size != seen.after.lines().size) return@verify "line count changed"
            if (!REPOSITORY_METHOD.containsMatchIn(seen.caretLineAfter())) return@verify "no real repository method on the line"
            phpLintError(seen.after)
        }
    }

    @Test
    @Order(4)
    fun `blank line between methods gets a complete method`() {
        val seed = Seed(BETWEEN_METHODS, "    }$CARET\n\n    #[Route('/admin/learning-team/replace-course'")
        scenarios.verify("newMethod", seed, { editor, _ -> scenarios.enter(editor) }) { seen ->
            val lines = seen.inserted.lines().filter { it.isNotBlank() }
            val declaration = lines.firstOrNull { !it.trimStart().startsWith("#[") }.orEmpty()
            val opened = seen.inserted.count { it == '{' }
            if (lines.size < MIN_METHOD_LINES) return@verify "expected at least $MIN_METHOD_LINES lines"
            if (!METHOD_DECLARATION.containsMatchIn(declaration)) return@verify "not a method declaration: $declaration"
            if (opened == 0 || opened != seen.inserted.count { it == '}' }) return@verify "unbalanced braces"
            phpLintError(seen.after)
        }
    }

    @Test
    @Order(5)
    fun `next suggestion action asks for a different variant that can be accepted`() {
        val seed = Seed(FLASH_LINE, "        \$this->addFlash(${CARET}")
        alternatives.verify(seed) { editor, driver -> editor.typeCharacter(driver, ' ') }
    }

    @Test
    @Order(6)
    fun `typing an open signature character by character gets the whole method`() {
        val seed = Seed(BETWEEN_METHODS, "    }\n\n    $CARET\n\n    #[Route('/admin/learning-team/replace-course'")
        val trigger = { editor: JEditorUiComponent, driver: Driver ->
            editor.typeText(driver, SIGNATURE_NAME, TYPING_PAUSE_MS)
            Thread.sleep(PAUSE_BEFORE_PAREN_MS)
            editor.typeCharacter(driver, '(')
            Thread.sleep(PAUSE_AFTER_PAREN_MS)
        }
        scenarios.verify("typedSignature", seed, trigger) { seen ->
            val method = seen.after.substringAfter(SIGNATURE_NAME).substringBefore(NEXT_METHOD)
            val lines = method.lines().filter { it.isNotBlank() }
            if (lines.size < MIN_METHOD_LINES) return@verify "expected a whole method of at least $MIN_METHOD_LINES lines"
            if (method.count { it == '{' } == 0) return@verify "no body was inserted"
            if (method.count { it == '{' } != method.count { it == '}' }) return@verify "unbalanced braces"
            if (method.count { it == '(' } != method.count { it == ')' }) return@verify "unbalanced parentheses"
            if (!method.contains("): ")) return@verify "no return type after the parameters"
            phpLintError(seen.after)
        }
    }

    private companion object {
        const val SIGNATURE_NAME = "public function restoreAction"
        const val NEXT_METHOD = "\n\n    #[Route('/admin/learning-team/replace-course'"
        const val TYPING_PAUSE_MS = 90L
        const val PAUSE_BEFORE_PAREN_MS = 700L
        const val PAUSE_AFTER_PAREN_MS = 900L
        const val EMPTY_ACTION_ANCHOR = "StudentLearningTeamResolver \$studentLearningTeamResolver\n    ): Response {"
        const val REDIRECT_LINE = "        return \$this->redirectToRoute('admin_learning_team_edit', ['id' => \$id]);"
        const val FLASH_LINE = "        \$this->addFlash('info', 'Change cancelled.');"
        const val FIND_LINE = "        \$participant = \$learningTeamParticipantRepository->find(\$participantId);"
        const val BETWEEN_METHODS = "    }\n\n    #[Route('/admin/learning-team/replace-course'"
        const val BODY_INDENT = "        "
        const val METHOD_END = "\n    }\n}"
        const val MIN_BODY_LINES = 3
        const val MIN_METHOD_LINES = 3
        val REPOSITORY_METHOD = Regex("""->\s*(find|findByLearningTeamId)\(""")
        val METHOD_DECLARATION = Regex("""^\s*(public|private|protected)\s+(static\s+)?function\s+\w+\(""")
    }
}
