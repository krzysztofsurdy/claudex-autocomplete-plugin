package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

@Service(Service.Level.APP)
class NextEditService(val scope: CoroutineScope) {
    var clock: () -> Long = SYSTEM_CLOCK
        private set

    private var sleeper: suspend (Long) -> Unit = SYSTEM_SLEEPER

    var timing = NextEditTiming(clock)
        private set

    var memory = RejectionMemory(clock)
        private set

    suspend fun sleep(ms: Long) = sleeper(ms)

    fun newGate(fileKey: () -> String) = NextEditGate(timing, memory, CaretTrigger(clock), fileKey)

    fun reset(clock: () -> Long = SYSTEM_CLOCK, sleeper: suspend (Long) -> Unit = SYSTEM_SLEEPER) {
        this.clock = clock
        this.sleeper = sleeper
        timing = NextEditTiming(clock)
        memory = RejectionMemory(clock)
    }

    companion object {
        private val SYSTEM_CLOCK: () -> Long = { System.nanoTime() / 1_000_000 }
        private val SYSTEM_SLEEPER: suspend (Long) -> Unit = { delay(it) }

        fun getInstance(): NextEditService = service()
    }
}
