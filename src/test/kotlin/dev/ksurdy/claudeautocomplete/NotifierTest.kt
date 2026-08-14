package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.FailureKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotifierTest {
    @Test
    fun throttlesPerKindForFiveMinutes() {
        val notifier = Notifier()
        assertTrue(notifier.shouldNotify(FailureKind.NotLoggedIn, 0))
        assertFalse(notifier.shouldNotify(FailureKind.NotLoggedIn, 1_000))
        assertTrue(notifier.shouldNotify(FailureKind.CliNotFound, 1_000))
        assertTrue(notifier.shouldNotify(FailureKind.NotLoggedIn, Notifier.THROTTLE_MS))
    }
}
