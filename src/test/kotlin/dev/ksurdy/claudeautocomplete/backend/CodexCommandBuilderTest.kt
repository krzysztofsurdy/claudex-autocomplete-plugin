package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodexCommandBuilderTest {
    private val binary = "/home/u/.nvm/versions/node/v22/bin/codex"

    private fun configValues(args: List<String>): List<String> =
        args.indices.filter { args[it] == "-c" }.map { args[it + 1] }

    @Test
    fun `exec command has non interactive flags`() {
        val args = CodexCommandBuilder.execCommand(binary, testCodexConfig())
        assertEquals(listOf(binary, "exec"), args.take(2))
        for (flag in listOf("--json", "--skip-git-repo-check", "--ephemeral")) assertContains(args, flag)
        assertEquals("read-only", args[args.indexOf("-s") + 1])
        assertEquals("gpt-5.3-codex", args[args.indexOf("-m") + 1])
    }

    @Test
    fun `prompt is not a positional argument so stdin is used`() {
        val args = CodexCommandBuilder.execCommand(binary, testCodexConfig())
        assertTrue(args.last() != "-" && !args.last().contains("CURSOR"))
    }

    @Test
    fun `config overrides select effort and slim the context`() {
        val values = configValues(CodexCommandBuilder.execCommand(binary, testCodexConfig(reasoningEffort = "minimal")))
        assertContains(values, "model_reasoning_effort=\"minimal\"")
        assertContains(values, "include_environment_context=false")
        assertContains(values, "include_permissions_instructions=false")
        assertContains(values, "project_doc_max_bytes=0")
        assertContains(values, "hide_agent_reasoning=true")
        assertContains(values, "web_search=\"disabled\"")
    }

    @Test
    fun `system prompt is passed as toml string developer instructions`() {
        val values = configValues(CodexCommandBuilder.execCommand(binary, testCodexConfig(customInstructions = "Use \"PSR-12\"")))
        val instructions = values.single { it.startsWith("developer_instructions=") }
        assertTrue(instructions.startsWith("developer_instructions=\""))
        assertTrue(instructions.endsWith("\""))
        assertTrue(!instructions.contains('\n'))
        assertContains(instructions, "<CURSOR/>")
        assertContains(instructions, "Use \\\"PSR-12\\\"")
        assertContains(instructions, "\\n")
    }

    @Test
    fun `toml string escapes control characters and backslashes`() {
        assertEquals("\"a\\\\b\\n\\t\\\"\"", CodexCommandBuilder.tomlString("a\\b\n\t\""))
    }

    @Test
    fun `app server command`() {
        assertEquals(listOf(binary, "app-server"), CodexCommandBuilder.appServerCommand(binary))
    }

    @Test
    fun `environment strips api keys but keeps codex home`() {
        val env = CodexCommandBuilder.environment(
            mapOf("OPENAI_API_KEY" to "k", "CODEX_API_KEY" to "k", "CODEX_HOME" to "/h", "PATH" to "/usr/bin", "CLAUDECODE" to "1"),
            binary,
        )
        assertNull(env["OPENAI_API_KEY"])
        assertNull(env["CODEX_API_KEY"])
        assertNull(env["CLAUDECODE"])
        assertEquals("/h", env["CODEX_HOME"])
        val path = env["PATH"]!!.split(":")
        assertEquals("/home/u/.nvm/versions/node/v22/bin", path.first())
        assertTrue(path.containsAll(listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")))
    }
}
