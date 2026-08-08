package dev.ksurdy.claudeautocomplete.backend

import java.io.File
import java.util.concurrent.TimeUnit

class ClaudeCliLocator(
    private val home: String = System.getProperty("user.home"),
    private val isExecutable: (String) -> Boolean = { File(it).let { f -> f.isFile && f.canExecute() } },
    private val shellLookup: () -> String? = ::lookupInLoginShell,
) {
    fun resolve(configuredPath: String): String? {
        val configured = configuredPath.trim()
        if (configured.isNotEmpty()) return configured.takeIf(isExecutable)
        val candidates = listOf(
            "$home/.local/bin/claude",
            "/opt/homebrew/bin/claude",
            "/usr/local/bin/claude",
            "$home/.claude/local/claude",
        )
        return candidates.firstOrNull(isExecutable) ?: shellLookup()?.takeIf(isExecutable)
    }

    companion object {
        private const val LOOKUP_TIMEOUT_SECONDS = 5L

        private fun lookupInLoginShell(): String? {
            val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: return null
            return try {
                val process = ProcessBuilder(shell, "-lc", "command -v claude")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                process.outputStream.close()
                if (!process.waitFor(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return null
                }
                process.inputStream.bufferedReader().readText().lines().lastOrNull { it.startsWith("/") }?.trim()
            } catch (_: java.io.IOException) {
                null
            }
        }
    }
}
