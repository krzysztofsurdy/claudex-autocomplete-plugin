package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.readAction
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import dev.ksurdy.claudeautocomplete.completion.CompletionCache
import dev.ksurdy.claudeautocomplete.completion.CompletionPostProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ClaudeInlineCompletionProvider : DebouncedInlineCompletionProvider() {
    override val id = InlineCompletionProviderID(ID)

    private val cache = CompletionCache(CACHE_CAPACITY)
    private val generation = AtomicLong()

    override fun isEnabled(event: InlineCompletionEvent): Boolean =
        ClaudeAutocompleteSettings.getInstance().state.enabled && isSupported(event)

    override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration {
        val gen = generation.incrementAndGet()
        if (request.event is InlineCompletionEvent.DirectCall || request.event is InlineCompletionEvent.ManualCall) {
            return Duration.ZERO
        }
        val delay = ClaudeAutocompleteSettings.getInstance().state.debounceMs.milliseconds
        if (delay > Duration.ZERO) publish(gen, ClaudeStatus.Waiting(System.currentTimeMillis()))
        return delay
    }

    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val settings = ClaudeAutocompleteSettings.getInstance()
        val manual = request.event is InlineCompletionEvent.ManualCall
        val gen = generation.get()
        val status = StatusService.getInstance()
        if (status.isBlocked(Instant.now())) {
            publish(gen, ClaudeStatus.LimitReached(status.blockedUntil))
            return InlineCompletionSuggestion.Empty
        }
        val snapshot = readAction {
            ContextCollector.collect(request, settings.state, force = manual)?.let { it to request.document.modificationStamp }
        }
        if (snapshot == null) {
            publish(gen, ClaudeStatus.Ready)
            return InlineCompletionSuggestion.Empty
        }
        val (context, stamp) = snapshot

        val config = settings.toClaudeConfig()
        val cacheKey = CompletionCache.key(context, config)
        val cached = if (manual) null else cache.get(cacheKey)
        if (cached != null) publish(gen, ClaudeStatus.Ready)
        val text = cached ?: run {
            val startedAt = System.currentTimeMillis()
            publish(gen, ClaudeStatus.Thinking(startedAt))
            status.recordModel(config.model)
            val indicator = if (settings.state.showInlineLoadingIndicator) {
                InlineLoadingIndicator.start(request.editor, request.endOffset)
            } else {
                null
            }
            val result = try {
                withContext(Dispatchers.IO) { BackendService.getInstance().backend.complete(context, config) }
            } catch (e: CancellationException) {
                publish(gen, ClaudeStatus.Ready)
                throw e
            } finally {
                indicator?.stop()
            }
            when (result) {
                is CompletionResult.Success -> {
                    val finishedAt = System.currentTimeMillis()
                    publish(gen, ClaudeStatus.Done(finishedAt - startedAt, finishedAt))
                    CompletionPostProcessor.process(result.text, context).also { if (it.isNotEmpty()) cache.put(cacheKey, it) }
                }
                CompletionResult.Empty -> {
                    publish(gen, ClaudeStatus.Ready)
                    ""
                }
                is CompletionResult.Failure -> {
                    handleFailure(gen, result)
                    ""
                }
            }
        }

        if (text.isEmpty() || request.document.modificationStamp != stamp) return InlineCompletionSuggestion.Empty
        return InlineCompletionSingleSuggestion.build { emit(InlineCompletionGrayTextElement(text)) }
    }

    private fun handleFailure(gen: Long, failure: CompletionResult.Failure) {
        val status = StatusService.getInstance()
        if (failure.kind != FailureKind.RateLimited) {
            publish(gen, ClaudeStatus.Error(failure.kind.label()))
            Notifier.getInstance().notifyFailure(failure.kind, failure.message)
            return
        }
        val now = Instant.now()
        val resetsAt = status.registerRateLimit(now)
        publish(gen, ClaudeStatus.LimitReached(resetsAt))
        val clock = StatusFormatter.clock(resetsAt, ZoneId.systemDefault())
        Notifier.getInstance().notifyFailure(failure.kind, "resets at $clock")
    }

    private fun publish(generation: Long, newStatus: ClaudeStatus) {
        if (this.generation.get() == generation) StatusService.getInstance().update(newStatus)
    }

    private fun isSupported(event: InlineCompletionEvent): Boolean =
        event is InlineCompletionEvent.DocumentChange ||
            event is InlineCompletionEvent.DirectCall ||
            event is InlineCompletionEvent.ManualCall

    companion object {
        const val ID = "dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider"
        private const val CACHE_CAPACITY = 32
    }
}
