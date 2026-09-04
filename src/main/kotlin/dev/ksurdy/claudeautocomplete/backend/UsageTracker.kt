package dev.ksurdy.claudeautocomplete.backend

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class UsageTracker(
    private val provider: ProviderKind = ProviderKind.Claude,
    private val clock: () -> Instant = Instant::now,
) {
    private val listeners = CopyOnWriteArrayList<(UsageLimits) -> Unit>()

    @Volatile
    var last: UsageLimits? = null
        private set

    fun publish(event: StreamEvent.RateLimit) {
        val limits = UsageLimits(event.fiveHour, event.sevenDay, event.status, clock(), provider)
        last = limits
        for (listener in listeners) {
            try {
                listener(limits)
            } catch (_: RuntimeException) {
            }
        }
    }

    fun addListener(listener: (UsageLimits) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}
