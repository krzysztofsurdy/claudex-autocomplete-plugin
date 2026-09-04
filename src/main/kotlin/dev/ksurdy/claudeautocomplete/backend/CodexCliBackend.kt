package dev.ksurdy.claudeautocomplete.backend

import com.google.gson.JsonObject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class CodexCliBackend(
    private val locate: (String) -> String? = CodexCliLocator::locate,
    private val usage: UsageTracker = UsageTracker(ProviderKind.Codex),
    private val processFactory: CodexProcessFactory = RealCodexProcessFactory,
) {
    val lastUsage: UsageLimits? get() = usage.last

    fun addUsageListener(listener: (UsageLimits) -> Unit): () -> Unit = usage.addListener(listener)

    suspend fun complete(context: CompletionContext, config: CodexConfig): CompletionResult {
        val binary = locate(config.codexPath) ?: return notFound()
        val process = try {
            processFactory.start(binary, config, CodexMode.Exec)
        } catch (e: IOException) {
            return CompletionResult.Failure(FailureKind.CliNotFound, e.message.orEmpty(), ProviderKind.Codex)
        }
        return guarded(process, config.requestTimeoutMs) { runExec(process, context) }
            ?: CompletionResult.Failure(FailureKind.Timeout, "Timed out after ${config.requestTimeoutMs} ms", ProviderKind.Codex)
    }

    suspend fun refreshUsage(config: CodexConfig): UsageLimits? {
        val binary = locate(config.codexPath) ?: return null
        val process = try {
            processFactory.start(binary, config, CodexMode.AppServer)
        } catch (e: IOException) {
            return null
        }
        val event = guarded(process, config.requestTimeoutMs) { readRateLimits(process) }
        if (event == null) return null
        usage.publish(event)
        return usage.last
    }

    private suspend fun <T> guarded(process: ClaudeProcess, timeoutMs: Int, block: suspend () -> T): T? = try {
        withTimeout(timeoutMs.toLong()) { block() }
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        null
    } finally {
        withContext(NonCancellable) { process.kill() }
    }

    private suspend fun runExec(process: ClaudeProcess, context: CompletionContext): CompletionResult {
        withContext(Dispatchers.IO) {
            process.send(PromptBuilder.userMessage(context))
            process.closeInput()
        }
        var message: String? = null
        var completed = false
        var lastError: String? = null
        for (line in process.lines) {
            when (val event = CodexJsonlParser.parse(line)) {
                is CodexEvent.AgentMessage -> message = event.text
                is CodexEvent.TurnFailed -> return CodexFailureMapper.map(event.message)
                is CodexEvent.Error -> {
                    if (CodexFailureMapper.isAuthRetry(event.message)) return CodexFailureMapper.map(event.message)
                    lastError = event.message
                }
                CodexEvent.TurnCompleted -> completed = true
                else -> Unit
            }
            if (completed) break
        }
        return when {
            message != null -> if (message.isBlank()) CompletionResult.Empty else CompletionResult.Success(message)
            completed -> CompletionResult.Empty
            lastError != null -> CodexFailureMapper.map(lastError)
            else -> CompletionResult.Failure(FailureKind.Other, "Codex CLI exited without a response", ProviderKind.Codex)
        }
    }

    private suspend fun readRateLimits(process: ClaudeProcess): StreamEvent.RateLimit? {
        withContext(Dispatchers.IO) { process.send(request(INITIALIZE_ID, "initialize", initializeParams())) }
        if (awaitResponse(process, INITIALIZE_ID)?.get("result") == null) return null
        withContext(Dispatchers.IO) {
            process.send(JsonObject().apply { addProperty("jsonrpc", "2.0"); addProperty("method", "initialized") }.toString())
            process.send(request(RATE_LIMITS_ID, "account/rateLimits/read", null))
        }
        val result = awaitResponse(process, RATE_LIMITS_ID)?.get("result")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return CodexJsonlParser.parseRateLimits(result)
    }

    private suspend fun awaitResponse(process: ClaudeProcess, id: Int): JsonObject? {
        for (line in process.lines) {
            val obj = StreamJsonParser.parseObject(line) ?: continue
            if (obj.get("id")?.takeIf { it.isJsonPrimitive }?.asInt == id) return obj
        }
        return null
    }

    private fun request(id: Int, method: String, params: JsonObject?): String = JsonObject().apply {
        addProperty("jsonrpc", "2.0")
        addProperty("id", id)
        addProperty("method", method)
        if (params != null) add("params", params)
    }.toString()

    private fun initializeParams(): JsonObject = JsonObject().apply {
        add("clientInfo", JsonObject().apply { addProperty("name", "claude-autocomplete"); addProperty("version", "0.1.0") })
    }

    private fun notFound() = CompletionResult.Failure(FailureKind.CliNotFound, "Codex CLI not found", ProviderKind.Codex)

    private companion object {
        const val INITIALIZE_ID = 1
        const val RATE_LIMITS_ID = 2
    }
}
