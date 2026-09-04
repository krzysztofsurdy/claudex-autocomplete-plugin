package dev.ksurdy.claudeautocomplete.backend

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object CodexCliLocator {
    private const val LOOKUP_TIMEOUT_SECONDS = 5L

    fun locate(configured: String): String? = locate(
        configured,
        System.getProperty("user.home"),
        { File(it).let { file -> file.isFile && file.canExecute() } },
        ::nvmCandidates,
        ::lookupInLoginShell,
    )

    fun locate(
        configured: String,
        home: String,
        isExecutable: (String) -> Boolean,
        nvmBinaries: (String) -> List<String>,
        shellLookup: () -> String?,
    ): String? {
        val path = configured.trim()
        if (path.isNotEmpty()) return path.takeIf(isExecutable)
        val fixed = listOf(
            "$home/.local/bin/codex",
            "/opt/homebrew/bin/codex",
            "/usr/local/bin/codex",
            "$home/.npm-global/bin/codex",
            "$home/.npm/bin/codex",
            "$home/.volta/bin/codex",
        )
        return fixed.firstOrNull(isExecutable)
            ?: nvmBinaries(home).firstOrNull(isExecutable)
            ?: shellLookup()?.takeIf(isExecutable)
    }

    private fun nvmCandidates(home: String): List<String> =
        File("$home/.nvm/versions/node").listFiles()?.sortedByDescending { it.name }?.map { "${it.path}/bin/codex" }.orEmpty()

    private fun lookupInLoginShell(): String? {
        val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val process = ProcessBuilder(shell, "-lc", "command -v codex").redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process.outputStream.close()
            if (!process.waitFor(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            process.inputStream.bufferedReader().readText().lines().lastOrNull { it.startsWith("/") }?.trim()
        } catch (_: IOException) {
            null
        }
    }
}
