package dev.ksurdy.claudeautocomplete.backend

class ClaudeCliBackend(
    private val persistent: CompletionBackend,
    private val oneShot: CompletionBackend,
) : CompletionBackend {
    constructor() : this(ClaudeCliLocator())

    private constructor(locator: ClaudeCliLocator) : this(PersistentClaudeBackend(locator), OneShotClaudeBackend(locator))

    private var consecutivePersistentFailures = 0
    private var lastConfig: ClaudeConfig? = null

    override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
        trackConfig(config)
        if (!config.persistentProcess || consecutivePersistentFailures >= MAX_PERSISTENT_FAILURES) {
            return oneShot.complete(context, config)
        }
        val result = persistent.complete(context, config)
        recordPersistentOutcome(result)
        return result
    }

    @Synchronized
    private fun trackConfig(config: ClaudeConfig) {
        if (lastConfig != config.copy(requestTimeoutMs = 0)) consecutivePersistentFailures = 0
        lastConfig = config.copy(requestTimeoutMs = 0)
    }

    @Synchronized
    private fun recordPersistentOutcome(result: CompletionResult) {
        consecutivePersistentFailures = when {
            result is CompletionResult.Failure && (result.kind == FailureKind.Other || result.kind == FailureKind.Timeout) ->
                consecutivePersistentFailures + 1
            result is CompletionResult.Failure -> consecutivePersistentFailures
            else -> 0
        }
    }

    override fun shutdown() {
        persistent.shutdown()
        oneShot.shutdown()
    }

    private companion object {
        const val MAX_PERSISTENT_FAILURES = 2
    }
}
