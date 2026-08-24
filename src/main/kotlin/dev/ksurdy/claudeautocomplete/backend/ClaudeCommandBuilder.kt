package dev.ksurdy.claudeautocomplete.backend

import java.io.File

object ClaudeCommandBuilder {
    private val EXTRA_PATH_DIRS = listOf("/opt/homebrew/bin", "/usr/local/bin")

    fun command(binary: String, config: ClaudeConfig, persistent: Boolean): List<String> = buildList {
        add(binary)
        add("-p")
        addAll(listOf("--model", config.model))
        add("--safe-mode")
        addAll(listOf("--tools", ""))
        add("--no-session-persistence")
        add("--strict-mcp-config")
        addAll(listOf("--setting-sources", ""))
        addAll(listOf("--system-prompt", PromptBuilder.systemPrompt(config.customInstructions)))
        addAll(listOf("--effort", config.effort))
        if (config.fallbackModel.isNotBlank()) addAll(listOf("--fallback-model", config.fallbackModel))
        if (persistent) {
            addAll(listOf("--input-format", "stream-json", "--output-format", "stream-json"))
            add("--verbose")
            add("--include-partial-messages")
        } else {
            addAll(listOf("--output-format", "stream-json"))
            add("--verbose")
            add("--disable-slash-commands")
        }
    }

    fun environment(base: Map<String, String>, binary: String, config: ClaudeConfig): Map<String, String> {
        val env = base.filterKeys { key ->
            key != "ANTHROPIC_API_KEY" && key != "ANTHROPIC_AUTH_TOKEN" && key != "CLAUDECODE" && !key.startsWith("CLAUDE_CODE_")
        }.toMutableMap()
        env["CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"] = "1"
        env["DISABLE_TELEMETRY"] = "1"
        env["MAX_THINKING_TOKENS"] = if (config.thinkingEnabled) config.thinkingBudgetTokens.toString() else "0"
        env["PATH"] = buildPath(binary, base["PATH"].orEmpty())
        return env
    }

    private fun buildPath(binary: String, existing: String): String {
        val binaryDir = File(binary).parent
        val dirs = listOfNotNull(binaryDir) + existing.split(":") + EXTRA_PATH_DIRS
        return dirs.filter { it.isNotEmpty() }.distinct().joinToString(":")
    }
}
