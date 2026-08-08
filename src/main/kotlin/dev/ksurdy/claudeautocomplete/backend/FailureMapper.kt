package dev.ksurdy.claudeautocomplete.backend

object FailureMapper {
    fun map(message: String): CompletionResult.Failure {
        val kind = when {
            message.contains("Not logged in", ignoreCase = true) -> FailureKind.NotLoggedIn
            message.contains("issue with the selected model", ignoreCase = true) -> FailureKind.InvalidModel
            else -> FailureKind.Other
        }
        return CompletionResult.Failure(kind, message.trim())
    }
}
