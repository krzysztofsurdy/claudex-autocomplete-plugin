package dev.ksurdy.claudeautocomplete.backend

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodexCliBackendIntegrationTest {
    private val config = testCodexConfig(
        codexPath = System.getenv("CODEX_PATH").orEmpty(),
        model = System.getenv("CODEX_MODEL") ?: "gpt-5.3-codex",
        requestTimeoutMs = 60000,
    )

    private fun enabled() = assumeTrue(System.getenv("CODEX_IT") == "1", "set CODEX_IT=1 (and be logged in with `codex login`) to run")

    @Test
    fun `codex returns a completion and usage`() {
        enabled()
        runBlocking {
            val backend = CodexCliBackend()
            val context = testContext(prefix = "<?php\n\nfunction add(int \$a, int \$b): int\n{\n    return ", suffix = "\n}\n")
            repeat(2) { index ->
                var result: CompletionResult? = null
                val ms = measureTimeMillis { result = backend.complete(context, config) }
                println("CODEX IT request=$index ms=$ms result=$result")
                assertIs<CompletionResult.Success>(result)
            }
            var usage: UsageLimits? = null
            val ms = measureTimeMillis { usage = backend.refreshUsage(config) }
            println("CODEX IT refreshUsage ms=$ms usage=$usage")
            assertTrue(usage?.fiveHour != null)
        }
    }
}
