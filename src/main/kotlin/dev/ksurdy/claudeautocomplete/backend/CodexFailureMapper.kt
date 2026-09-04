package dev.ksurdy.claudeautocomplete.backend

object CodexFailureMapper {
    private val NOT_LOGGED_IN = listOf("401", "not logged in", "log in", "login", "authentication required", "missing bearer")
    private val RATE_LIMITED = listOf("usage limit", "rate limit", "429", "quota", "limit reached")
    private val MODEL_MISSING = listOf("does not exist", "not found", "not supported", "unknown model", "access to it")

    fun map(message: String): CompletionResult.Failure {
        val lower = message.lowercase()
        val kind = when {
            NOT_LOGGED_IN.any(lower::contains) -> FailureKind.NotLoggedIn
            RATE_LIMITED.any(lower::contains) -> FailureKind.RateLimited
            lower.contains("model") && MODEL_MISSING.any(lower::contains) -> FailureKind.InvalidModel
            else -> FailureKind.Other
        }
        return CompletionResult.Failure(kind, message.trim(), ProviderKind.Codex)
    }

    fun isAuthRetry(message: String): Boolean = message.startsWith("Reconnecting") && message.contains("401")
}
