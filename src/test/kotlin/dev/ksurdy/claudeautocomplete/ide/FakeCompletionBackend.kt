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

    @Volatile
    var nextEditGate: CompletableDeferred<Unit>? = null

    var partials: List<String> = emptyList()

    val queued = java.util.concurrent.ConcurrentLinkedQueue<CompletionResult>()

    @Volatile
    var responder: ((CompletionContext) -> CompletionResult)? = null

    val cancellations = java.util.concurrent.atomic.AtomicInteger()

    override suspend fun complete(context: CompletionContext, config: BackendConfig, onText: (String) -> Unit): CompletionResult {
        contexts += context
        configs += config
        partials.forEach(onText)
        try {
            gate?.await()
            if (context.nextEdit != null) nextEditGate?.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancellations.incrementAndGet()
            throw e
        }
        return queued.poll() ?: responder?.invoke(context) ?: result
    }

    override suspend fun refreshUsage(config: BackendConfig): UsageLimits? = null

    val warmUps = java.util.concurrent.atomic.AtomicInteger()

    override suspend fun warmUp(config: BackendConfig) {
        warmUps.incrementAndGet()
    }

    override fun shutdown() = Unit
}
