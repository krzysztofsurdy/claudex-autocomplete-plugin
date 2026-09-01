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
    val importedClasses: List<OpenFileSnippet> = emptyList(),
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

enum class FailureKind { NotLoggedIn, CliNotFound, InvalidModel, Timeout, RateLimited, Other }

interface CompletionBackend {
    suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult
    fun shutdown()
}

data class UsageWindow(val utilization: Double, val resetsAt: java.time.Instant?)

data class UsageLimits(
    val fiveHour: UsageWindow?,
    val sevenDay: UsageWindow?,
    val status: String?,
    val observedAt: java.time.Instant,
)
