package dev.ksurdy.claudeautocomplete.backend

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
        val config = testConfig(claudePath = "", persistentProcess = persistent, requestTimeoutMs = 30000)
        try {
            repeat(3) { index ->
                var result: CompletionResult? = null
                val ms = measureTimeMillis { result = backend.complete(context, config) }
                println("IT persistent=$persistent request=$index ms=$ms result=$result")
                val success = assertIs<CompletionResult.Success>(result)
                assertTrue(success.text.isNotBlank())
            }
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
}
