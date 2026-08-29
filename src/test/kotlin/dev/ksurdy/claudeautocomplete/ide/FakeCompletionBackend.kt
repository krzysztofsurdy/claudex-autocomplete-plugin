package dev.ksurdy.claudeautocomplete.ide

import dev.ksurdy.claudeautocomplete.backend.ClaudeConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionBackend
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList

class FakeCompletionBackend(var result: CompletionResult) : CompletionBackend {
    val contexts = CopyOnWriteArrayList<CompletionContext>()

    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
        contexts += context
        gate?.await()
        return result
    }

    override fun shutdown() = Unit
}
