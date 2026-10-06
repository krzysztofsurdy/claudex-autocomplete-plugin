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
    fun claudeMessagesNameTheClaudeCodeCli() {
        assertTrue(Notifier.contentFor(Notifier.CLAUDE, FailureKind.NotLoggedIn, "")!!.startsWith("Claude Code CLI is not logged in"))
        assertTrue(Notifier.contentFor(Notifier.CLAUDE, FailureKind.CliNotFound, "")!!.startsWith("Claude Code CLI not found"))
    }

    @Test
    fun escapesRawCliMessages() {
        val text = Notifier.contentFor(Notifier.CLAUDE, FailureKind.InvalidModel, "<b>bad</b> & worse")!!
        assertFalse(text.contains("<b>bad"), text)
        assertTrue(text.contains("&lt;b&gt;bad&lt;/b&gt; &amp; worse"), text)
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

class NotifierWordingTest {
    @Test
    fun usesSettingsPathStyleWithoutBackticks() {
        listOf(FailureKind.NotLoggedIn, FailureKind.CliNotFound, FailureKind.InvalidModel).forEach { kind ->
            listOf(Notifier.CLAUDE, Notifier.CODEX).forEach { provider ->
                val text = Notifier.contentFor(provider, kind, "")!!
                assertFalse(text.contains('`'), text)
                assertFalse(text.contains("Settings >"), text)
            }
        }
        assertTrue(Notifier.contentFor(Notifier.CLAUDE, FailureKind.CliNotFound, "")!!.contains("Settings | Tools | Claudex Autocomplete"))
    }
}
