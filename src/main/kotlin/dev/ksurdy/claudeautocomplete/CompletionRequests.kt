package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.completion.CacheKey
import dev.ksurdy.claudeautocomplete.completion.CompletionCache
import dev.ksurdy.claudeautocomplete.completion.InFlightMatch
import dev.ksurdy.claudeautocomplete.completion.InFlightOrigin
import dev.ksurdy.claudeautocomplete.completion.InFlightVerdict
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
class CompletionRequests(private val scope: CoroutineScope) {
    val cache = CompletionCache(CACHE_CAPACITY)

    private var current: RunningCompletion? = null

    @Synchronized
    fun attach(context: CompletionContext, config: BackendConfig): Attachment? {
        val running = current ?: return null
        val progress = running.progress.value
        if (progress.isFinished) {
            current = null
            return null
        }
        return when (val verdict = InFlightMatch.decide(running.origin, originOf(context, config), progress.streamed)) {
            is InFlightVerdict.Reuse -> Attachment(running, verdict.typed)
            InFlightVerdict.Diverged -> {
                cancel()
                null
            }
        }
    }

    @Synchronized
    fun start(context: CompletionContext, config: BackendConfig, cacheKey: CacheKey): Attachment {
        current?.cancel()
        val running = RunningCompletion(originOf(context, config), context, cacheKey, cache, config, scope)
        current = running
        return Attachment(running, "")
    }

    @Synchronized
    fun cancel() {
        current?.cancel()
        current = null
    }

    private fun originOf(context: CompletionContext, config: BackendConfig) =
        InFlightOrigin(CompletionCache.scope(context, config), context.prefix, context.suffix)

    companion object {
        private const val CACHE_CAPACITY = 32

        fun getInstance(): CompletionRequests = service()
    }
}
