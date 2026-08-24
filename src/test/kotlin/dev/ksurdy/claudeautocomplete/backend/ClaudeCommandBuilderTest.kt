package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeCommandBuilderTest {
    private val binary = "/home/u/.local/bin/claude"

    private fun valueAfter(args: List<String>, flag: String): String = args[args.indexOf(flag) + 1]

    @Test
    fun `common flags are present`() {
        val args = ClaudeCommandBuilder.command(binary, testConfig(), persistent = false)
        assertEquals(binary, args.first())
        for (flag in listOf("-p", "--safe-mode", "--no-session-persistence", "--strict-mcp-config")) assertContains(args, flag)
        assertEquals("haiku", valueAfter(args, "--model"))
        assertEquals("", valueAfter(args, "--tools"))
        assertEquals("", valueAfter(args, "--setting-sources"))
        assertEquals("low", valueAfter(args, "--effort"))
        assertContains(valueAfter(args, "--system-prompt"), "<CURSOR/>")
        assertFalse(args.contains("--bare"))
    }

    @Test
    fun `one shot uses json output and disables slash commands`() {
        val args = ClaudeCommandBuilder.command(binary, testConfig(), persistent = false)
        assertContains(args, "--disable-slash-commands")
        assertFalse(args.contains("--input-format"))
    }

    @Test
    fun `persistent uses stream json and keeps slash commands`() {
        val args = ClaudeCommandBuilder.command(binary, testConfig(), persistent = true)
        assertEquals("stream-json", valueAfter(args, "--output-format"))
        assertEquals("stream-json", valueAfter(args, "--input-format"))
        assertContains(args, "--verbose")
        assertContains(args, "--include-partial-messages")
        assertFalse(args.contains("--disable-slash-commands"))
    }

    @Test
    fun `fallback model only when set`() {
        assertFalse(ClaudeCommandBuilder.command(binary, testConfig(), false).contains("--fallback-model"))
        val args = ClaudeCommandBuilder.command(binary, testConfig(fallbackModel = "sonnet"), false)
        assertEquals("sonnet", valueAfter(args, "--fallback-model"))
    }

    @Test
    fun `environment strips claude and api key variables`() {
        val base = mapOf(
            "HOME" to "/home/u", "PATH" to "/usr/bin", "ANTHROPIC_API_KEY" to "k", "ANTHROPIC_AUTH_TOKEN" to "t",
            "CLAUDECODE" to "1", "CLAUDE_CODE_ENTRYPOINT" to "cli", "CLAUDE_CODE_SSE_PORT" to "1",
        )
        val env = ClaudeCommandBuilder.environment(base, binary, testConfig())
        assertEquals("/home/u", env["HOME"])
        assertNull(env["ANTHROPIC_API_KEY"])
        assertNull(env["ANTHROPIC_AUTH_TOKEN"])
        assertNull(env["CLAUDECODE"])
        assertNull(env["CLAUDE_CODE_ENTRYPOINT"])
        assertNull(env["CLAUDE_CODE_SSE_PORT"])
        assertEquals("1", env["CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"])
        assertEquals("1", env["DISABLE_TELEMETRY"])
    }

    @Test
    fun `thinking disabled sets zero tokens`() {
        assertEquals("0", ClaudeCommandBuilder.environment(emptyMap(), binary, testConfig())["MAX_THINKING_TOKENS"])
    }

    @Test
    fun `thinking enabled uses budget`() {
        val env = ClaudeCommandBuilder.environment(emptyMap(), binary, testConfig(thinkingEnabled = true, thinkingBudgetTokens = 2048))
        assertEquals("2048", env["MAX_THINKING_TOKENS"])
    }

    @Test
    fun `path includes binary dir and common locations`() {
        val path = ClaudeCommandBuilder.environment(mapOf("PATH" to "/usr/bin"), binary, testConfig())["PATH"]!!
        val parts = path.split(":")
        assertEquals("/home/u/.local/bin", parts.first())
        assertTrue(parts.containsAll(listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")))
        assertEquals(parts.size, parts.toSet().size)
    }

    @Test
    fun `path is created when missing from base`() {
        val path = ClaudeCommandBuilder.environment(emptyMap(), binary, testConfig())["PATH"]!!
        assertContains(path, "/opt/homebrew/bin")
    }

    @Test
    fun `one shot streams json with verbose to receive usage events`() {
        val args = ClaudeCommandBuilder.command(binary, testConfig(), persistent = false)
        assertEquals("stream-json", valueAfter(args, "--output-format"))
        assertContains(args, "--verbose")
    }
}
