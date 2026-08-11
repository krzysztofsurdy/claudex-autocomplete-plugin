package dev.ksurdy.claudeautocomplete.backend

class ClaudeCliBackend(
    private val persistent: CompletionBackend,
    private val oneShot: CompletionBackend,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) : CompletionBackend {
    constructor() : this(ClaudeCliLocator())

    private constructor(locator: ClaudeCliLocator) : this(PersistentClaudeBackend(locator), OneShotClaudeBackend(locator))

    private var consecutivePersistentFailures = 0
    private var breakerOpenedAt = 0L
    private var lastConfig: ClaudeConfig? = null

    override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
        if (!config.persistentProcess || !shouldTryPersistent(config)) {
            return oneShot.complete(context, config)
        }
        val result = persistent.complete(context, config)
        recordPersistentOutcome(result)
        return result
    }

    @Synchronized
    private fun shouldTryPersistent(config: ClaudeConfig): Boolean {
        val normalized = config.copy(requestTimeoutMs = 0)
        if (lastConfig != normalized) consecutivePersistentFailures = 0
        lastConfig = normalized
        if (consecutivePersistentFailures < MAX_PERSISTENT_FAILURES) return true
        return clock() - breakerOpenedAt >= cooldownMs
    }

    @Synchronized
    private fun recordPersistentOutcome(result: CompletionResult) {
        when {
            result is CompletionResult.Failure && result.kind == FailureKind.Other -> {
                consecutivePersistentFailures++
                if (consecutivePersistentFailures >= MAX_PERSISTENT_FAILURES) breakerOpenedAt = clock()
            }
            result is CompletionResult.Failure -> Unit
            else -> consecutivePersistentFailures = 0
        }
    }

    override fun shutdown() {
        persistent.shutdown()
        oneShot.shutdown()
    }

    private companion object {
        const val MAX_PERSISTENT_FAILURES = 2
        const val DEFAULT_COOLDOWN_MS = 60_000L
    }
}
