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

class NotifierTextTest {
    @Test
    fun loginHintDependsOnProvider() {
        assertTrue(Notifier.contentFor(Notifier.CLAUDE, FailureKind.NotLoggedIn, "")!!.contains("/login"))
        assertTrue(Notifier.contentFor(Notifier.CODEX, FailureKind.NotLoggedIn, "")!!.contains("codex login"))
    }

    @Test
    fun cliNotFoundShowsInstallHintForCodex() {
        assertTrue(Notifier.contentFor(Notifier.CODEX, FailureKind.CliNotFound, "")!!.contains("npm i -g @openai/codex"))
    }

    @Test
    fun throttleIsPerProvider() {
        val notifier = Notifier()
        assertTrue(notifier.shouldNotify(FailureKind.NotLoggedIn, 0, Notifier.CLAUDE))
        assertTrue(notifier.shouldNotify(FailureKind.NotLoggedIn, 1, Notifier.CODEX))
        assertFalse(notifier.shouldNotify(FailureKind.NotLoggedIn, 2, Notifier.CODEX))
    }

    @Test
    fun rateLimitMentionsProviderAndReset() {
        assertTrue(Notifier.contentFor(Notifier.CODEX, FailureKind.RateLimited, "resets at 14:30")!!.startsWith("Codex usage limit reached, resets at 14:30"))
    }
}
