package dev.ksurdy.claudeautocomplete.backend

import dev.ksurdy.claudeautocomplete.completion.CompletionPostProcessor
import dev.ksurdy.claudeautocomplete.completion.TriggerRules
import dev.ksurdy.claudeautocomplete.nextedit.EditHunk
import dev.ksurdy.claudeautocomplete.nextedit.NextEditInput
import dev.ksurdy.claudeautocomplete.nextedit.NextEditParser
import dev.ksurdy.claudeautocomplete.nextedit.NextEditTarget
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClaudeCliBackendIntegrationTest {
    private val context = testContext(
        prefix = "<?php\n\nfunction add(int \$a, int \$b): int\n{\n    return ",
        suffix = "\n}\n",
        multiline = false,
    )

    private fun enabled() = assumeTrue(System.getenv("CLAUDE_IT") == "1", "set CLAUDE_IT=1 to run against the real claude CLI")

    private fun exercise(persistent: Boolean) = runBlocking {
        val backend = ClaudeCliBackend()
        val seen = mutableListOf<UsageLimits>()
        backend.addUsageListener { seen += it }
        val config = testConfig(claudePath = "", persistentProcess = persistent, requestTimeoutMs = 30000)
        try {
            repeat(3) { index ->
                var result: CompletionResult? = null
                val ms = measureTimeMillis { result = backend.complete(context, config) }
                println("IT persistent=$persistent request=$index ms=$ms result=$result")
                val success = assertIs<CompletionResult.Success>(result)
                assertTrue(success.text.isNotBlank())
            }
            println("IT usage persistent=$persistent events=${seen.size} last=${backend.lastUsage}")
            assertTrue(seen.isNotEmpty())
            assertTrue(backend.lastUsage?.fiveHour != null)
        } finally {
            backend.shutdown()
        }
    }

    @Test
    fun `one shot returns a completion`() {
        enabled()
        exercise(persistent = false)
    }

    @Test
    fun `persistent returns completions across requests`() {
        enabled()
        exercise(persistent = true)
    }

    private val phpClass = "<?php\n\nclass UserService\n{\n    public function __construct(private UserRepository \$users)\n    {\n    }\n\n"

    private fun realistic(name: String, prefix: String, suffix: String, mode: String = "auto") = runBlocking {
        val multiline = TriggerRules.isMultiline(prefix, suffix, mode)
        val context = testContext(prefix = prefix, suffix = suffix, multiline = multiline, indent = "4 spaces")
        val backend = ClaudeCliBackend()
        try {
            val config = testConfig(claudePath = "", requestTimeoutMs = 30000)
            backend.complete(context, config)
            var result: CompletionResult? = null
            val ms = measureTimeMillis { result = backend.complete(context, config) }
            val processed = (result as? CompletionResult.Success)?.let { CompletionPostProcessor.process(it.text, context) }
            println("IT case=$name multiline=$multiline ms=$ms raw=$result processed=<<$processed>>")
            assertIs<CompletionResult.Success>(result)
        } finally {
            backend.shutdown()
        }
    }

    @Test
    fun `php method body on blank line`() {
        enabled()
        realistic(
            "method-body",
            phpClass + "    public function findActive(string \$email): ?User\n    {\n        ",
            "\n    }\n}\n",
        )
    }

    @Test
    fun `php mid statement single line`() {
        enabled()
        realistic("mid-statement", phpClass + "    public function count(): int\n    {\n        \$x = \$this->", ";\n    }\n}\n")
    }

    @Test
    fun `php array literal`() {
        enabled()
        realistic("array-literal", phpClass + "    public function roles(): array\n    {\n        return [\n            ", "\n        ];\n    }\n}\n")
    }

    private fun nextEditCase(name: String, file: String, edits: List<EditHunk>, caretLine: Int, expectedLine: Int?) = runBlocking {
        val input = NextEditInput(edits, file, 1, caretLine)
        val context = testContext(indent = "4 spaces").copy(nextEdit = input)
        val backend = ClaudeCliBackend()
        try {
            val config = testConfig(claudePath = "", requestTimeoutMs = 30000)
            var result: CompletionResult? = null
            val ms = measureTimeMillis { result = backend.complete(context, config) }
            val raw = (result as? CompletionResult.Success)?.text
            val parsed = raw?.let { NextEditParser.parse(it) }
            val target = NextEditTarget.choose(parsed, file, caretLine)
            println("IT next-edit case=$name ms=$ms raw=<<$raw>> parsed=$parsed target=$target")
            assertIs<CompletionResult.Success>(result)
            if (expectedLine != null) {
                assertTrue(target != null && target.startLine == expectedLine, "expected an edit at line $expectedLine, got $target")
            }
        } finally {
            backend.shutdown()
        }
    }

    @Test
    fun `next edit renames the other usage of a renamed parameter`() {
        enabled()
        val file = "<?php\n\nclass Calc\n{\n    public function add(int \$bar): int\n    {\n        \$total = \$a + 1;\n        return \$total;\n    }\n}"
        nextEditCase("rename-param", file, listOf(EditHunk(5, "    public function add(int \$a): int", "    public function add(int \$bar): int")), 5, 7)
    }

    @Test
    fun `next edit updates the constructor after a new field`() {
        enabled()
        val file = "<?php\n\nclass User\n{\n    private string \$name;\n    private string \$email;\n\n    public function __construct(string \$name)\n    {\n        \$this->name = \$name;\n    }\n}"
        nextEditCase("new-field", file, listOf(EditHunk(6, "", "    private string \$email;")), 6, null)
    }

    @Test
    fun `next edit says none when nothing follows`() {
        enabled()
        val file = "<?php\n\n// TODO\nfunction ping(): string\n{\n    return 'pong';\n}"
        nextEditCase("nothing", file, listOf(EditHunk(3, "// todo", "// TODO")), 3, null)
    }

    private val controller = "<?php\n\nnamespace App\\Controller;\n\nuse App\\Repository\\LearningTeamParticipantRepository;\n" +
        "use Symfony\\Bundle\\FrameworkBundle\\Controller\\AbstractController;\nuse Symfony\\Component\\HttpFoundation\\Response;\n\n" +
        "class LearningTeamController extends AbstractController\n{\n" +
        "    public function __construct(private LearningTeamParticipantRepository \$learningTeamParticipantRepository)\n    {\n    }\n\n" +
        "    public function show(int \$id): Response\n    {\n" +
        "        \$participant = \$this->learningTeamParticipantRepository->find(\$id);\n\n" +
        "        return \$this->render('team/show.html.twig', ['participant' => \$participant]);\n    }\n\n"

    private val prose = Regex("^\\s*(The cursor|The caret|Wait|Hmm|Actually|I |I'|Here|Sure|Note|This |Let me|Okay)", RegexOption.MULTILINE)

    private fun proseFree(name: String, prefix: String, suffix: String) = runBlocking {
        val multiline = TriggerRules.isMultiline(prefix, suffix, "auto")
        val context = testContext(prefix = prefix, suffix = suffix, multiline = multiline, indent = "4 spaces")
        val runs = System.getenv("CLAUDE_IT_RUNS")?.toIntOrNull() ?: 3
        val backend = ClaudeCliBackend()
        try {
            val config = testConfig(claudePath = "", requestTimeoutMs = 30000)
            repeat(runs) { index ->
                val result = backend.complete(context, config)
                val raw = (result as? CompletionResult.Success)?.text.orEmpty()
                val processed = CompletionPostProcessor.interpret(raw, context)
                println("IT prose case=$name run=$index multiline=$multiline source=${processed.source} raw=<<$raw>> processed=<<${processed.text}>>")
                assertIs<CompletionResult.Success>(result)
                assertFalse(prose.containsMatchIn(processed.text), "prose in suggestion: ${processed.text}")
                assertFalse(processed.text.contains("cursor", ignoreCase = true), "prose in suggestion: ${processed.text}")
            }
        } finally {
            backend.shutdown()
        }
    }

    @Test
    fun `mid line redirect call yields no prose`() {
        enabled()
        proseFree(
            "redirect-mid-line",
            controller + "    public function delete(int \$id): Response\n    {\n        return \$this->redirectToRoute(",
            "['id' => \$id]);\n    }\n}\n",
        )
    }

    @Test
    fun `blank line between two methods yields no prose`() {
        enabled()
        proseFree(
            "between-methods",
            controller + "    public function edit(int \$id): Response\n    {\n        return \$this->render('team/edit.html.twig');\n    }\n\n",
            "\n    public function delete(int \$id): Response\n    {\n        return \$this->redirectToRoute('team_index');\n    }\n}\n",
        )
    }

    @Test
    fun `blank line between two methods proposes a member in most runs`() {
        enabled()
        val prefix = controller.trimEnd('\n') + "\n    "
        val suffix = "\n\n    #[Route('/admin/learning-team/delete', name: 'admin_learning_team_delete')]\n    public function delete(int \$id): Response\n    {\n        return \$this->redirectToRoute('team_index');\n    }\n}\n"
        val context = testContext(prefix = prefix, suffix = suffix, multiline = TriggerRules.isMultiline(prefix, suffix, "auto"), indent = "4 spaces")
        assertTrue(context.multiline)
        val runs = System.getenv("CLAUDE_IT_RUNS")?.toIntOrNull() ?: 5
        runBlocking {
            val backend = ClaudeCliBackend()
            try {
                val config = testConfig(claudePath = "", requestTimeoutMs = 30000)
                val hits = (0 until runs).count { index ->
                    val raw = (backend.complete(context, config) as? CompletionResult.Success)?.text.orEmpty()
                    val processed = CompletionPostProcessor.interpret(raw, context)
                    println("IT between-methods run=$index source=${processed.source} raw=<<$raw>> processed=<<${processed.text}>>")
                    processed.text.isNotBlank()
                }
                println("IT between-methods hits=$hits runs=$runs")
                assertTrue(hits * 2 > runs, "only $hits of $runs requests proposed a member")
            } finally {
                backend.shutdown()
            }
        }
    }

    @Test
    fun `archive action signature continues the parameter list and writes real code`() {
        enabled()
        val prefix = controller + "    public function approveAction(int \$id): Response\n    {\n" +
            "        \$participant = \$this->learningTeamParticipantRepository->find(\$id);\n" +
            "        \$participant->approve();\n        \$this->learningTeamParticipantRepository->save(\$participant);\n\n" +
            "        return \$this->redirectToRoute('team_show', ['id' => \$id]);\n    }\n\n" +
            "    #[Route('/admin/learning-team/{id}/archive', name: 'admin_learning_team_archive')]\n    public function archiveAction("
        val suffix = "\n\n    #[Route('/admin/learning-team/{id}/delete', name: 'admin_learning_team_delete')]\n" +
            "    public function deleteAction(int \$id): Response\n    {\n" +
            "        \$participant = \$this->learningTeamParticipantRepository->find(\$id);\n" +
            "        \$this->learningTeamParticipantRepository->remove(\$participant);\n\n" +
            "        return \$this->redirectToRoute('team_index');\n    }\n}\n"
        val context = testContext(prefix = prefix, suffix = suffix, multiline = TriggerRules.isMultiline(prefix, suffix, "auto"), indent = "4 spaces")
        val stub = Regex("not implemented|TODO|FIXME|throw new \\\\?LogicException|\\{\\s*\\}", RegexOption.IGNORE_CASE)
        val runs = System.getenv("CLAUDE_IT_RUNS")?.toIntOrNull() ?: 3
        runBlocking {
            val backend = ClaudeCliBackend()
            try {
                val config = testConfig(claudePath = "", requestTimeoutMs = 30000)
                val hits = (0 until runs).count { index ->
                    val raw = (backend.complete(context, config) as? CompletionResult.Success)?.text.orEmpty()
                    val processed = CompletionPostProcessor.interpret(raw, context).text
                    println("IT archive run=$index raw=<<$raw>> processed=<<$processed>>")
                    processed.isNotBlank() && !stub.containsMatchIn(processed) && !processed.trimStart().startsWith(")")
                }
                println("IT archive hits=$hits runs=$runs")
                assertTrue(hits * 2 > runs, "only $hits of $runs requests wrote real code that continues the parameter list")
            } finally {
                backend.shutdown()
            }
        }
    }

    @Test
    fun `refresh usage returns fresh limits`() {
        enabled()
        runBlocking {
            val backend = ClaudeCliBackend()
            val seen = mutableListOf<UsageLimits>()
            backend.addUsageListener { seen += it }
            try {
                var usage: UsageLimits? = null
                val ms = measureTimeMillis { usage = backend.refreshUsage(testConfig(claudePath = "", requestTimeoutMs = 30000)) }
                println("IT refresh ms=$ms usage=$usage listenerEvents=${seen.size}")
                assertTrue(usage?.fiveHour != null)
                assertTrue(seen.isNotEmpty())
            } finally {
                backend.shutdown()
            }
        }
    }
}
