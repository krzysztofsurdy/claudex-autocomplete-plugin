package dev.ksurdy.claudeautocomplete.nextedit

object InlineWait {
    suspend fun await(maxWaits: Int, isBusy: suspend () -> Boolean, onWait: () -> Unit, sleep: suspend () -> Unit): Boolean {
        var waits = 0
        while (isBusy()) {
            if (waits++ == maxWaits) return false
            onWait()
            sleep()
        }
        return true
    }
}
