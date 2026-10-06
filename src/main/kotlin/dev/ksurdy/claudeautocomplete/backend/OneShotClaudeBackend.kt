package dev.ksurdy.claudeautocomplete.backend

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class OneShotClaudeBackend(
    private val locator: ClaudeCliLocator = ClaudeCliLocator(),
    private val usage: UsageTracker = UsageTracker(),
    private val processFactory: ClaudeProcessFactory = RealClaudeProcessFactory,
) : CompletionBackend {
    override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
        val binary = locator.resolve(config.claudePath)
            ?: return CompletionResult.Failure(FailureKind.CliNotFound, "Claude Code CLI not found")
        val process = try {
            processFactory.start(binary, config, persistent = false)
        } catch (e: IOException) {
            return CompletionResult.Failure(FailureKind.CliNotFound, e.message.orEmpty())
        }
        return try {
            withTimeout(config.requestTimeoutMs.toLong()) { run(process, context) }
        } catch (e: TimeoutCancellationException) {
            CompletionResult.Failure(FailureKind.Timeout, "Timed out after ${config.requestTimeoutMs} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            CompletionResult.Failure(FailureKind.Other, e.message.orEmpty())
        } finally {
            withContext(NonCancellable) { process.kill() }
        }
    }

    private suspend fun run(process: ClaudeProcess, context: CompletionContext): CompletionResult {
        withContext(Dispatchers.IO) {
            process.send(PromptBuilder.userMessage(context))
            process.closeInput()
        }
        var result: StreamEvent.Result? = null
        var rejected = false
        for (line in process.lines) {
            when (val event = StreamJsonParser.parse(line)) {
                is StreamEvent.Result -> result = event
                is StreamEvent.RateLimit -> {
                    usage.publish(event)
                    rejected = rejected || event.status == REJECTED
                }
                else -> Unit
            }
        }
        return ResultMapper.toCompletionResult(result, rateLimited = rejected)
    }

    override fun shutdown() = Unit

    private companion object {
        const val REJECTED = "rejected"
    }
}
