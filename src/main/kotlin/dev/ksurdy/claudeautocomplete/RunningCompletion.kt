package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.completion.CacheKey
import dev.ksurdy.claudeautocomplete.completion.CompletionCache
import dev.ksurdy.claudeautocomplete.completion.CompletionPostProcessor
import dev.ksurdy.claudeautocomplete.completion.CompletionReply
import dev.ksurdy.claudeautocomplete.completion.InFlightOrigin
import dev.ksurdy.claudeautocomplete.completion.StreamingCompletion
import dev.ksurdy.claudeautocomplete.completion.TypedTrim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RunningCompletion(
    val origin: InFlightOrigin,
    val context: CompletionContext,
    private val cacheKey: CacheKey,
    private val cache: CompletionCache,
    config: BackendConfig,
    scope: CoroutineScope,
) {
    data class Progress(
        val text: String = "",
        val streamed: String = "",
        val result: CompletionResult? = null,
        val processed: CompletionPostProcessor.Processed? = null,
        val aborted: Boolean = false,
    ) {
        val isFinished: Boolean get() = result != null || aborted
    }

    private val stream = StreamingCompletion(context)
    private val state = MutableStateFlow(Progress())

    val progress: StateFlow<Progress> get() = state

    private val job: Job = scope.launch { run(config) }.also { job ->
        job.invokeOnCompletion { cause -> if (cause != null) state.update { it.copy(aborted = true) } }
    }

    fun cancel() = job.cancel()

    private suspend fun run(config: BackendConfig) {
        val result = withContext(Dispatchers.IO) {
            BackendService.getInstance().engine.complete(context, config) { raw ->
                stream.onPartial(raw)
                val streamed = CompletionReply.parse(raw, context, final = false).content
                state.update { it.copy(text = stream.text, streamed = streamed) }
            }
        }
        if (result is CompletionResult.Success) {
            stream.onFinal(result.text)
            if (stream.text.isNotEmpty()) cache.put(cacheKey, stream.text)
        }
        state.update { it.copy(text = stream.text, result = result, processed = stream.processed) }
    }
}

class Attachment(val running: RunningCompletion, val typed: String) {
    class Followed(val progress: RunningCompletion.Progress, val retry: Boolean)

    suspend fun follow(onDelta: suspend (String) -> Unit): Followed {
        val trim = TypedTrim(typed)
        var last = running.progress.value
        running.progress
            .transformWhile { emit(it); !it.isFinished }
            .onEach { progress ->
                last = progress
                val delta = trim.advance(progress.text)
                if (delta.isNotEmpty()) onDelta(delta)
            }
            .takeWhile { !trim.diverged }
            .collect()
        val unusable = trim.diverged || (last.result is CompletionResult.Success && !trim.hasRest)
        return Followed(last, retry = typed.isNotEmpty() && unusable)
    }
}
