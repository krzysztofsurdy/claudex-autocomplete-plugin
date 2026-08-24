package dev.ksurdy.claudeautocomplete.backend

object ResultMapper {
    fun toCompletionResult(result: StreamEvent.Result?, streamed: String = "", rateLimited: Boolean = false): CompletionResult = when {
        result == null -> CompletionResult.Failure(FailureKind.Other, "Claude CLI exited without a result")
        result.isError && rateLimited -> CompletionResult.Failure(FailureKind.RateLimited, result.text.trim())
        result.isError -> FailureMapper.map(result.text)
        else -> {
            val text = streamed.ifEmpty { result.text }
            if (text.isBlank()) CompletionResult.Empty else CompletionResult.Success(text)
        }
    }
}
