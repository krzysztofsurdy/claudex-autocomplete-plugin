package dev.ksurdy.claudeautocomplete.nextedit

class CaretTrigger(private val clock: () -> Long) {
    private var lastEditAt: Long? = null

    @Synchronized
    fun recordEdit() {
        lastEditAt = clock()
    }

    @Synchronized
    fun shouldRequest(fromLine: Int, toLine: Int): Boolean {
        val editedAt = lastEditAt ?: return false
        return fromLine != toLine && clock() - editedAt <= WINDOW_MS
    }

    companion object {
        const val WINDOW_MS = 10_000L
    }
}
