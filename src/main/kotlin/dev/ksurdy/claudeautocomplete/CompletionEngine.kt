package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.CompletionRouter
import dev.ksurdy.claudeautocomplete.backend.UsageLimits

interface CompletionEngine {
    suspend fun complete(context: CompletionContext, config: BackendConfig): CompletionResult

    suspend fun refreshUsage(config: BackendConfig): UsageLimits?

    fun shutdown()
}

class RouterEngine(private val router: CompletionRouter) : CompletionEngine {
    override suspend fun complete(context: CompletionContext, config: BackendConfig): CompletionResult =
        router.complete(context, config)

    override suspend fun refreshUsage(config: BackendConfig): UsageLimits? = router.refreshUsage(config)

    override fun shutdown() = router.shutdown()
}
