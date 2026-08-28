package dev.ksurdy.claudeautocomplete.backend

import dev.ksurdy.claudeautocomplete.completion.CompletionPostProcessor
import dev.ksurdy.claudeautocomplete.completion.TriggerRules
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
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
