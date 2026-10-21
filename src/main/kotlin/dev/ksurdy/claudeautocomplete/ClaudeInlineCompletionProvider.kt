package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.DefaultInlineCompletionInsertHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionInsertEnvironment
import com.intellij.codeInsight.inline.completion.InlineCompletionInsertHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderPresentation
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.UserDataHolderBase
import dev.ksurdy.claudeautocomplete.alternatives.AlternativeRequest
import dev.ksurdy.claudeautocomplete.alternatives.AlternativeSuggestion
import dev.ksurdy.claudeautocomplete.alternatives.AlternativeTexts
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import dev.ksurdy.claudeautocomplete.completion.CacheKey
import dev.ksurdy.claudeautocomplete.completion.CompletionCache
import dev.ksurdy.claudeautocomplete.completion.CompletionPostProcessor
import dev.ksurdy.claudeautocomplete.completion.Gate
import dev.ksurdy.claudeautocomplete.completion.ReplySource
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
    override val providerPresentation: InlineCompletionProviderPresentation = SuggestionHint()

    override val insertHandler: InlineCompletionInsertHandler = object : DefaultInlineCompletionInsertHandler() {
        override fun afterInsertion(environment: InlineCompletionInsertEnvironment, elements: List<InlineCompletionElement>) {
            super.afterInsertion(environment, elements)
            requests.prefetchAfterAccept(environment.editor)
        }
    }

    private val requests = CompletionRequests.getInstance()
    private val generation = AtomicLong()

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        val settings = ClaudeAutocompleteSettings.getInstance()
        return settings.isActive && isSupported(event) && !skipped(EditorGates.blockedBy(event, settings.state))
    }

    override fun restartOn(event: InlineCompletionEvent): Boolean {
        val editor = when (event) {
            is InlineCompletionEvent.DirectCall -> event.editor
            is InlineCompletionEvent.ManualCall -> event.editor
            else -> return false
        }
        return AlternativeRequest.isPending(editor)
    }

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
        if (skipped(EditorGates.blockedAfterDebounce(request.event, settings.state))) {
            publish(gen, ClaudeStatus.Ready)
            return InlineCompletionSuggestion.Empty
        }
        val alternative = AlternativeRequest.take(request.editor)
        val status = StatusService.getInstance()
        if (status.isBlocked(Instant.now())) {
            publish(gen, ClaudeStatus.LimitReached(status.blockedUntil))
            return InlineCompletionSuggestion.Empty
        }
        val context = readAction { ContextCollector.collect(request, settings.state, force = manual) }
        if (context == null) {
            requests.cancel()
            publish(gen, ClaudeStatus.Ready)
            return InlineCompletionSuggestion.Empty
        }

        val config = settings.toBackendConfig()
        val indicatorShown = settings.state.showInlineLoadingIndicator
        if (alternative != null) return alternativeSuggestion(request, context, config, alternative, gen, indicatorShown)
        val cacheKey = CompletionCache.key(context, config)
        val cached = if (manual) null else requests.cache.get(cacheKey)
        if (cached != null) {
            publish(gen, ClaudeStatus.Ready)
            return InlineCompletionSingleSuggestion.build { emit(InlineCompletionGrayTextElement(cached)) }
        }
        val attachment = if (manual) null else requests.attach(context, config)
        return InlineCompletionSingleSuggestion.build(
            UserDataHolderBase(),
            streamCompletion(request, context, config, cacheKey, gen, indicatorShown, attachment),
        )
    }

    private suspend fun alternativeSuggestion(
        request: InlineCompletionRequest,
        context: CompletionContext,
        config: BackendConfig,
        alternative: AlternativeRequest,
        gen: Long,
        indicatorShown: Boolean,
    ): InlineCompletionSuggestion {
        val startedAt = System.currentTimeMillis()
        publish(gen, ClaudeStatus.Thinking(startedAt))
        StatusService.getInstance().recordModel(config.activeModel())
        val indicator = if (indicatorShown) InlineLoadingIndicator.start(request.editor, request.endOffset) else null
        try {
            val fresh = AlternativeTexts.fetchDistinct(alternative.previousTexts) { retry ->
                requestAlternative(context.copy(alternativeTo = alternative.previousTexts, distinctRetry = retry), config, gen)
            }
            if (fresh == null) LOG.debug { "Alternative dropped: no distinct suggestion shown=${alternative.previousTexts.size}" }
            return AlternativeSuggestion(AlternativeTexts.variants(fresh, alternative.previousTexts))
        } catch (e: CancellationException) {
            publish(gen, ClaudeStatus.Ready)
            LOG.debug { "Alternative cancelled: ms=${System.currentTimeMillis() - startedAt}" }
            throw e
        } finally {
            indicator?.stop()
        }
    }

    private suspend fun requestAlternative(context: CompletionContext, config: BackendConfig, gen: Long): String {
        val startedAt = System.currentTimeMillis()
        LOG.debug { "Requesting alternative: provider=${config.provider} model=${config.activeModel()} retry=${context.distinctRetry} shown=${context.alternativeTo.size}" }
        return when (val result = withContext(Dispatchers.IO) { BackendService.getInstance().engine.complete(context, config) }) {
            is CompletionResult.Success -> {
                val finishedAt = System.currentTimeMillis()
                publish(gen, ClaudeStatus.Done(finishedAt - startedAt, finishedAt))
                val text = CompletionPostProcessor.interpret(result.text, context).text
                LOG.debug { "Alternative received: ms=${finishedAt - startedAt} length=${text.length} duplicate=${AlternativeTexts.isDuplicate(text, context.alternativeTo)}" }
                text
            }
            CompletionResult.Empty -> {
                publish(gen, ClaudeStatus.Ready)
                ""
            }
            is CompletionResult.Failure -> {
                handleFailure(gen, result, config.provider)
                ""
            }
        }
    }

    private fun streamCompletion(
        request: InlineCompletionRequest,
        context: CompletionContext,
        config: BackendConfig,
        cacheKey: CacheKey,
        gen: Long,
        indicatorShown: Boolean,
        reused: Attachment?,
    ): Flow<InlineCompletionElement> = channelFlow {
        val startedAt = System.currentTimeMillis()
        publish(gen, ClaudeStatus.Thinking(startedAt))
        StatusService.getInstance().recordModel(config.activeModel())
        if (reused == null) {
            LOG.debug { "Requesting completion: provider=${config.provider} model=${config.activeModel()} language=${context.languageId}" }
        } else {
            LOG.debug { "Reusing request: typedLength=${reused.typed.length} streamedLength=${reused.running.progress.value.streamed.length}" }
        }
        val indicator = if (indicatorShown) InlineLoadingIndicator.start(request.editor, request.endOffset) else null
        var firstShownAt = 0L
        var shownLength = 0
        val show: suspend (String) -> Unit = { delta ->
            if (firstShownAt == 0L) firstShownAt = System.currentTimeMillis()
            shownLength += delta.length
            indicator?.stop()
            send(InlineCompletionGrayTextElement(delta))
        }
        try {
            var followed = (reused ?: requests.start(context, config, cacheKey)).follow(show)
            while (followed.retry) {
                LOG.debug { "Reused request unusable: requesting again" }
                followed = requests.start(context, config, cacheKey).follow(show)
            }
            val progress = followed.progress
            when (val result = progress.result) {
                is CompletionResult.Success -> {
                    val finishedAt = System.currentTimeMillis()
                    publish(gen, ClaudeStatus.Done(finishedAt - startedAt, finishedAt))
                    val firstShownMs = if (firstShownAt == 0L) finishedAt - startedAt else firstShownAt - startedAt
                    logReceived(context, result.text, progress.text, finishedAt - startedAt, firstShownMs)
                    progress.processed?.let(::logGuardCuts)
                    if (progress.text.isEmpty()) logEmptied(result.text, progress.processed)
                }
                CompletionResult.Empty -> {
                    publish(gen, ClaudeStatus.Ready)
                    LOG.debug { "Completion empty: backend returned blank text rawLength=0 ms=${System.currentTimeMillis() - startedAt}" }
                }
                is CompletionResult.Failure -> handleFailure(gen, result, config.provider)
                null -> publish(gen, ClaudeStatus.Ready)
            }
        } catch (e: CancellationException) {
            publish(gen, ClaudeStatus.Ready)
            LOG.debug { "Completion cancelled: ms=${System.currentTimeMillis() - startedAt} shownLength=$shownLength" }
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

    private fun logGuardCuts(processed: CompletionPostProcessor.Processed) = processed.cuts.forEach { cut ->
        LOG.debug { "Completion guarded: guard=${cut.guard.label} linesBefore=${cut.linesBefore} linesAfter=${cut.linesAfter}" }
    }

    private fun logEmptied(raw: String, processed: CompletionPostProcessor.Processed?) = LOG.debug {
        val lastCut = processed?.cuts?.lastOrNull()
        val cause = when {
            processed?.source == ReplySource.ProseRejected -> "prose guard"
            processed?.extractedLength == 0 -> "empty completion block"
            lastCut != null -> lastCut.guard.label
            else -> "post-processing"
        }
        "Completion emptied: cause=$cause source=${processed?.source} rawLength=${raw.length} extractedLength=${processed?.extractedLength}"
    }

    private fun handleFailure(gen: Long, failure: CompletionResult.Failure, provider: ProviderKind) =
        publish(gen, FailureReporter.report(failure, provider))

    private fun publish(generation: Long, newStatus: ClaudeStatus) {
        if (this.generation.get() == generation) StatusService.getInstance().update(newStatus)
    }

    private fun skipped(gate: Gate?): Boolean {
        if (gate != null) LOG.debug { "Request skipped: gate=${gate.name}" }
        return gate != null
    }

    private fun isSupported(event: InlineCompletionEvent): Boolean =
        event is InlineCompletionEvent.DocumentChange ||
            event is InlineCompletionEvent.DirectCall ||
            event is InlineCompletionEvent.ManualCall

    companion object {
        const val ID = "dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider"

        fun manualCall(editor: Editor): InlineCompletionEvent.ManualCall =
            InlineCompletionEvent.ManualCall(editor, InlineCompletionProviderID(ID), UserDataHolderBase())

        private val LOG = Logger.getInstance(ClaudeInlineCompletionProvider::class.java)
    }
}
