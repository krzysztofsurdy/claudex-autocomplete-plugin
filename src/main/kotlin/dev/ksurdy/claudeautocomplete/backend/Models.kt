package dev.ksurdy.claudeautocomplete.backend

data class CompletionContext(
    val filePath: String,
    val languageId: String,
    val prefix: String,
    val suffix: String,
    val openFiles: List<OpenFileSnippet>,
    val multiline: Boolean,
    val maxLines: Int,
    val indent: String = "",
    val prefixTruncated: Boolean = false,
    val suffixTruncated: Boolean = false,
)

data class OpenFileSnippet(val path: String, val languageId: String, val content: String)

data class ClaudeConfig(
    val claudePath: String,
    val model: String,
    val fallbackModel: String,
    val effort: String,
    val thinkingEnabled: Boolean,
    val thinkingBudgetTokens: Int,
    val requestTimeoutMs: Int,
    val persistentProcess: Boolean,
    val customInstructions: String,
)

sealed interface CompletionResult {
    data class Success(val text: String) : CompletionResult
    data object Empty : CompletionResult
    data class Failure(val kind: FailureKind, val message: String) : CompletionResult
}

enum class FailureKind { NotLoggedIn, CliNotFound, InvalidModel, Timeout, Other }

interface CompletionBackend {
    suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult
    fun shutdown()
}
