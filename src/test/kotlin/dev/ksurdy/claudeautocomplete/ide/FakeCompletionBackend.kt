package dev.ksurdy.claudeautocomplete.ide

import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.UsageLimits
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList

class FakeCompletionBackend(var result: CompletionResult) : CompletionEngine {
    val contexts = CopyOnWriteArrayList<CompletionContext>()
    val configs = CopyOnWriteArrayList<BackendConfig>()

    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun complete(context: CompletionContext, config: BackendConfig): CompletionResult {
        contexts += context
        configs += config
        gate?.await()
        return result
    }

    override suspend fun refreshUsage(config: BackendConfig): UsageLimits? = null

    override fun shutdown() = Unit
}
