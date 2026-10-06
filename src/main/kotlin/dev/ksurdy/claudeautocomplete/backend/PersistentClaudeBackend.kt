package dev.ksurdy.claudeautocomplete.backend

import com.google.gson.JsonObject
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class PersistentClaudeBackend(
    private val locator: ClaudeCliLocator = ClaudeCliLocator(),
    private val usage: UsageTracker = UsageTracker(),
    private val processFactory: ClaudeProcessFactory = RealClaudeProcessFactory,
) : CompletionBackend {
    private class Session(val process: ClaudeProcess, val key: ClaudeConfig, var used: Boolean = false,
        @Volatile var inFlight: Boolean = false,
    )

    private val mutex = Mutex()
    @Volatile
    private var session: Session? = null

    override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult =
        mutex.withLock {
            val current = when (val start = obtainSession(config)) {
                is SessionStart.Failed -> return start.failure
                is SessionStart.Ready -> start.session
            }
            try {
                withTimeout(config.requestTimeoutMs.toLong()) { request(current, context) }
            } catch (e: TimeoutCancellationException) {
                discard(current)
                CompletionResult.Failure(FailureKind.Timeout, "Timed out after ${config.requestTimeoutMs} ms")
            } catch (e: CancellationException) {
                if (current.inFlight) withContext(NonCancellable) { interrupt(current) }
                throw e
            } catch (e: IOException) {
                discard(current)
                CompletionResult.Failure(FailureKind.Other, e.message.orEmpty())
            }
        }

    private sealed interface SessionStart {
        data class Ready(val session: Session) : SessionStart
        data class Failed(val failure: CompletionResult.Failure) : SessionStart
    }

    private fun obtainSession(config: ClaudeConfig): SessionStart {
        val key = config.copy(requestTimeoutMs = 0, persistentProcess = true)
        session?.let { existing ->
            if (existing.key == key && existing.process.isAlive) return SessionStart.Ready(existing)
            discard(existing)
        }
        val binary = locator.resolve(config.claudePath)
            ?: return SessionStart.Failed(CompletionResult.Failure(FailureKind.CliNotFound, "Claude Code CLI not found"))
        return try {
            SessionStart.Ready(Session(processFactory.start(binary, config, persistent = true), key).also { session = it })
        } catch (e: IOException) {
            SessionStart.Failed(CompletionResult.Failure(FailureKind.CliNotFound, e.message.orEmpty()))
        }
    }

    private suspend fun request(current: Session, context: CompletionContext): CompletionResult {
        if (current.used) {
            current.inFlight = true
            send(current, userLine("/clear"))
            awaitResult(current)?.let { if (it.isError) return FailureMapper.map(it.text) }
        }
        current.used = true
        current.inFlight = true
        send(current, userLine(PromptBuilder.userMessage(context)))
        val streamed = StringBuilder()
        var final: StreamEvent.Result? = null
        var rejected = false
        for (line in current.process.lines) {
            when (val event = StreamJsonParser.parse(line)) {
                is StreamEvent.TextDelta -> streamed.append(event.text)
                is StreamEvent.RateLimit -> {
                    usage.publish(event)
                    rejected = rejected || event.status == REJECTED
                }
                is StreamEvent.Result -> {
                    current.inFlight = false
                    final = event
                    break
                }
                else -> Unit
            }
        }
        if (final == null) discard(current)
        return ResultMapper.toCompletionResult(final, streamed.toString(), rejected)
    }

    private suspend fun send(current: Session, line: String) {
        withContext(Dispatchers.IO) { current.process.send(line) }
    }

    private suspend fun awaitResult(current: Session): StreamEvent.Result? {
        for (line in current.process.lines) {
            val event = StreamJsonParser.parse(line)
            if (event is StreamEvent.RateLimit) usage.publish(event)
            if (event is StreamEvent.Result) {
                current.inFlight = false
                return event
            }
        }
        discard(current)
        return null
    }

    private suspend fun interrupt(current: Session) {
        val drained = try {
            withContext(Dispatchers.IO) { current.process.send(interruptLine()) }
            withTimeoutOrNull(INTERRUPT_DRAIN_MS) { awaitResult(current) }
        } catch (e: IOException) {
            null
        }
        if (drained == null) discard(current)
    }

    private fun discard(target: Session) {
        target.process.kill()
        if (session === target) session = null
    }

    override fun shutdown() {
        session?.let(::discard)
        session = null
    }

    private fun userLine(content: String): String {
        val message = JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", content)
        }
        return JsonObject().apply {
            addProperty("type", "user")
            add("message", message)
        }.toString()
    }

    private fun interruptLine(): String {
        val request = JsonObject().apply { addProperty("subtype", "interrupt") }
        return JsonObject().apply {
            addProperty("type", "control_request")
            addProperty("request_id", UUID.randomUUID().toString())
            add("request", request)
        }.toString()
    }

    private companion object {
        const val INTERRUPT_DRAIN_MS = 500L
        const val REJECTED = "rejected"
    }
}
