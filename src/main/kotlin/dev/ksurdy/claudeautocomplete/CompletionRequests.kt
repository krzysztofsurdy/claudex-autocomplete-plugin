package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import dev.ksurdy.claudeautocomplete.completion.CacheKey
import dev.ksurdy.claudeautocomplete.completion.CompletionCache
import dev.ksurdy.claudeautocomplete.completion.InFlightMatch
import dev.ksurdy.claudeautocomplete.completion.InFlightOrigin
import dev.ksurdy.claudeautocomplete.completion.InFlightVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

@Service(Service.Level.APP)
class CompletionRequests(private val scope: CoroutineScope) {
    val cache = CompletionCache(CACHE_CAPACITY)

    @Volatile
    var prefetch: Job? = null
        private set

    private var current: RunningCompletion? = null
    private var epoch = 0

    @Synchronized
    fun attach(context: CompletionContext, config: BackendConfig): Attachment? {
        supersedePrefetchWatcher()
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
        supersedePrefetchWatcher()
        return begin(context, config, cacheKey)
    }

    @Synchronized
    fun cancel() {
        supersedePrefetchWatcher()
        current?.cancel()
        current = null
    }

    fun prefetchAfterAccept(editor: Editor) {
        val settings = ClaudeAutocompleteSettings.getInstance()
        if (!settings.state.isPrefetchActive || StatusService.getInstance().isBlocked(Instant.now())) return
        val config = settings.toBackendConfig()
        synchronized(this) {
            supersedePrefetchWatcher()
            val mine = epoch
            prefetch = scope.launch { prefetchNext(editor, config, mine) }
        }
    }

    private suspend fun prefetchNext(editor: Editor, config: BackendConfig, mine: Int) {
        val state = ClaudeAutocompleteSettings.getInstance().state
        val context = readAction {
            if (editor.isDisposed) return@readAction null
            val request = ClaudeInlineCompletionProvider.manualCall(editor).toRequest() ?: return@readAction null
            ContextCollector.collect(request, state)
        } ?: return
        val key = CompletionCache.key(context, config)
        if (cache.get(key) != null) return
        val running = startPrefetch(context, config, key, mine) ?: return
        LOG.debug { "Prefetching next suggestion: provider=${config.provider} model=${config.activeModel()} language=${context.languageId}" }
        val result = running.progress.first { it.isFinished }.result
        if (result is CompletionResult.Failure) reportFailure(result, config)
    }

    @Synchronized
    private fun startPrefetch(context: CompletionContext, config: BackendConfig, key: CacheKey, mine: Int): RunningCompletion? {
        if (mine != epoch || StatusService.getInstance().isBlocked(Instant.now())) return null
        val running = current
        if (running != null && !running.progress.value.isFinished && running.origin == originOf(context, config)) return null
        return begin(context, config, key).running
    }

    private fun reportFailure(failure: CompletionResult.Failure, config: BackendConfig) {
        if (failure.kind == FailureKind.Timeout || failure.kind == FailureKind.Other) {
            LOG.debug { "Prefetch failed: kind=${failure.kind}" }
            return
        }
        StatusService.getInstance().update(FailureReporter.report(failure, config.provider))
    }

    private fun supersedePrefetchWatcher() {
        epoch++
        prefetch?.cancel()
        prefetch = null
    }

    private fun begin(context: CompletionContext, config: BackendConfig, cacheKey: CacheKey): Attachment {
        current?.cancel()
        val running = RunningCompletion(originOf(context, config), context, cacheKey, cache, config, scope)
        current = running
        return Attachment(running, "")
    }

    private fun originOf(context: CompletionContext, config: BackendConfig) =
        InFlightOrigin(CompletionCache.scope(context, config), context.prefix, context.suffix)

    companion object {
        private const val CACHE_CAPACITY = 32
        private val LOG = Logger.getInstance(CompletionRequests::class.java)

        fun getInstance(): CompletionRequests = service()
    }
}
