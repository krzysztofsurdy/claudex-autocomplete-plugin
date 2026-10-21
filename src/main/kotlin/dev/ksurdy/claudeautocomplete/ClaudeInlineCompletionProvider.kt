package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.UserDataHolderBase
import dev.ksurdy.claudeautocomplete.alternatives.AlternativeRequest
import dev.ksurdy.claudeautocomplete.alternatives.AlternativeSuggestion
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import dev.ksurdy.claudeautocomplete.completion.CompletionCache
import dev.ksurdy.claudeautocomplete.completion.StreamingCompletion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ClaudeInlineCompletionProvider : DebouncedInlineCompletionProvider() {
    override val id = InlineCompletionProviderID(ID)

    private val cache = CompletionCache(CACHE_CAPACITY)
    private val generation = AtomicLong()

    override fun isEnabled(event: InlineCompletionEvent): Boolean =
        ClaudeAutocompleteSettings.getInstance().isActive && isSupported(event)

    override fun restartOn(event: InlineCompletionEvent): Boolean =
        event is InlineCompletionEvent.DirectCall && AlternativeRequest.isPending(event.editor)

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
        val alternative = AlternativeRequest.take(request.editor)
        val status = StatusService.getInstance()
        if (status.isBlocked(Instant.now())) {
            publish(gen, ClaudeStatus.LimitReached(status.blockedUntil))
            return InlineCompletionSuggestion.Empty
        }
        val context = readAction { ContextCollector.collect(request, settings.state, force = manual) }
        if (context == null) {
            publish(gen, ClaudeStatus.Ready)
            return InlineCompletionSuggestion.Empty
        }

        val config = settings.toBackendConfig()
        val indicatorShown = settings.state.showInlineLoadingIndicator
        if (alternative != null) {
            val fresh = streamCompletion(request, context.copy(alternativeTo = alternative.previousTexts), config, null, gen, indicatorShown)
            return AlternativeSuggestion(fresh, alternative.previousTexts)
        }
        val cacheKey = CompletionCache.key(context, config)
        val cached = if (manual) null else cache.get(cacheKey)
        if (cached != null) {
            publish(gen, ClaudeStatus.Ready)
            return InlineCompletionSingleSuggestion.build { emit(InlineCompletionGrayTextElement(cached)) }
        }
        return InlineCompletionSingleSuggestion.build(UserDataHolderBase(), streamCompletion(request, context, config, cacheKey, gen, indicatorShown))
    }

    private fun streamCompletion(
        request: InlineCompletionRequest,
        context: CompletionContext,
        config: BackendConfig,
        cacheKey: String?,
        gen: Long,
        indicatorShown: Boolean,
    ): Flow<InlineCompletionElement> = channelFlow {
        val startedAt = System.currentTimeMillis()
        publish(gen, ClaudeStatus.Thinking(startedAt))
        StatusService.getInstance().recordModel(config.activeModel())
        LOG.debug { "Requesting completion: provider=${config.provider} model=${config.activeModel()} language=${context.languageId}" }
        val indicator = if (indicatorShown) InlineLoadingIndicator.start(request.editor, request.endOffset) else null
        val stream = StreamingCompletion(context)
        var firstShownAt = 0L
        try {
            val result = withContext(Dispatchers.IO) {
                BackendService.getInstance().engine.complete(context, config) { raw ->
                    val delta = stream.onPartial(raw)
                    if (delta.isNotEmpty()) {
                        if (firstShownAt == 0L) firstShownAt = System.currentTimeMillis()
                        indicator?.stop()
                        trySend(InlineCompletionGrayTextElement(delta))
                    }
                }
            }
            when (result) {
                is CompletionResult.Success -> {
                    val finishedAt = System.currentTimeMillis()
                    publish(gen, ClaudeStatus.Done(finishedAt - startedAt, finishedAt))
                    val delta = stream.onFinal(result.text)
                    val firstShownMs = if (firstShownAt == 0L) finishedAt - startedAt else firstShownAt - startedAt
                    logReceived(context, result.text, stream.text, finishedAt - startedAt, firstShownMs)
                    if (delta.isNotEmpty()) send(InlineCompletionGrayTextElement(delta))
                    if (stream.text.isNotEmpty()) cacheKey?.let { cache.put(it, stream.text) }
                }
                CompletionResult.Empty -> publish(gen, ClaudeStatus.Ready)
                is CompletionResult.Failure -> handleFailure(gen, result, config.provider)
            }
        } catch (e: CancellationException) {
            publish(gen, ClaudeStatus.Ready)
            throw e
        } finally {
            indicator?.stop()
        }
    }.buffer(Channel.UNLIMITED)

    private fun logReceived(context: CompletionContext, raw: String, shown: String, ms: Long, firstShownMs: Long) = LOG.debug {
        "Completion received: ms=$ms firstShownMs=$firstShownMs multiline=${context.multiline} emptyBody=${context.emptyBody} " +
            "maxLines=${context.maxLines} rawLines=${raw.lines().size} shownLines=${shown.lines().size} " +
            "caretColumn=${context.prefix.substringAfterLast('\n').length} " +
            "rawLead=${raw.takeWhile { it.isWhitespace() }.length} shownLead=${shown.takeWhile { it.isWhitespace() }.length}"
    }

    private fun handleFailure(gen: Long, failure: CompletionResult.Failure, provider: ProviderKind) =
        publish(gen, FailureReporter.report(failure, provider))

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
        private val LOG = Logger.getInstance(ClaudeInlineCompletionProvider::class.java)
    }
}
