package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import dev.ksurdy.claudeautocomplete.backend.testCodexConfig
import dev.ksurdy.claudeautocomplete.backend.testConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CompletionCacheTest {
    private fun at(prefix: String, suffix: String = "", scope: String = "scope", multiline: Boolean = false) =
        CacheKey.of(scope, prefix, suffix, multiline)

    @Test
    fun `stores and returns values`() {
        val cache = CompletionCache(2)
        cache.put(at("a"), "1")
        assertEquals("1", cache.get(at("a")))
        assertNull(cache.get(at("b")))
    }

    @Test
    fun `evicts least recently used`() {
        val cache = CompletionCache(2)
        cache.put(at("a"), "1")
        cache.put(at("b"), "2")
        cache.get(at("a"))
        cache.put(at("c"), "3")
        assertNull(cache.get(at("b")))
        assertEquals("1", cache.get(at("a")))
        assertEquals("3", cache.get(at("c")))
    }

    @Test
    fun `serves the rest of a suggestion after the user typed its start`() {
        val cache = CompletionCache(4)
        cache.put(at("foo ", "\nend"), "abcdef")
        assertEquals("def", cache.get(at("foo abc", "\nend")))
        assertEquals("bcdef", cache.get(at("foo a", "\nend")))
        assertEquals("abcdef", cache.get(at("foo ", "\nend")))
    }

    @Test
    fun `never serves an empty remainder`() {
        val cache = CompletionCache(4)
        cache.put(at("foo "), "abc")
        assertNull(cache.get(at("foo abc")))
        assertNull(cache.get(at("foo abcd")))
    }

    @Test
    fun `typing that departs from the suggestion misses`() {
        val cache = CompletionCache(4)
        cache.put(at("foo "), "abcdef")
        assertNull(cache.get(at("foo abx")))
        assertNull(cache.get(at("foo x")))
        assertNull(cache.get(at("fo")))
    }

    @Test
    fun `a changed suffix misses`() {
        val cache = CompletionCache(4)
        cache.put(at("foo ", ")"), "abcdef")
        assertNull(cache.get(at("foo abc", "]")))
        assertNull(cache.get(at("foo abc", "")))
    }

    @Test
    fun `another scope misses`() {
        val cache = CompletionCache(4)
        cache.put(at("foo ", scope = "one"), "abcdef")
        assertNull(cache.get(at("foo abc", scope = "two")))
    }

    @Test
    fun `an unrelated short prefix does not match`() {
        val cache = CompletionCache(4)
        cache.put(at("zzz"), "abcdef")
        assertNull(cache.get(at("abc")))
        assertNull(cache.get(at("xxabc")))
    }

    @Test
    fun `matches inside a prefix longer than the key tail`() {
        val far = "x".repeat(900)
        val cache = CompletionCache(4)
        cache.put(at("${far}foo "), "abcdef")
        assertEquals("def", cache.get(at("${far}foo abc")))
        assertNull(cache.get(at("${"y".repeat(900)}foo abc")))
    }

    @Test
    fun `forward hits expire with the entry`() {
        var now = 0L
        val cache = CompletionCache(2, ttlMs = 60_000, clock = { now })
        cache.put(at("foo "), "abcdef")
        now = 59_999
        assertEquals("def", cache.get(at("foo abc")))
        now = 60_001
        assertNull(cache.get(at("foo abc")))
        assertNull(cache.get(at("foo ")))
    }

    @Test
    fun `a forward hit keeps the entry alive for eviction`() {
        val cache = CompletionCache(2)
        cache.put(at("a "), "alpha")
        cache.put(at("b "), "beta")
        assertEquals("pha", cache.get(at("a al")))
        cache.put(at("c "), "gamma")
        assertNull(cache.get(at("b ")))
        assertEquals("alpha", cache.get(at("a ")))
    }

    @Test
    fun `the newest entry wins when several extend the prefix`() {
        val cache = CompletionCache(4)
        cache.put(at("foo "), "abcdef")
        cache.put(at("foo a"), "bcxyz")
        assertEquals("xyz", cache.get(at("foo abc")))
    }

    @Test
    fun `a multi-line rest is not served to a single-line request`() {
        val cache = CompletionCache(4)
        cache.put(at("foo ", multiline = true), "abc\ndef")
        assertNull(cache.get(at("foo a", multiline = false)))
        assertEquals("\ndef", cache.get(at("foo abc", multiline = true)))
        assertNull(cache.get(at("foo abc", multiline = false)))
    }

    @Test
    fun `a single-line rest is served whatever the request mode`() {
        val cache = CompletionCache(4)
        cache.put(at("foo ", multiline = true), "abc\ndef")
        assertEquals("ef", cache.get(at("foo abc\nd", multiline = false)))
        assertEquals("def", cache.get(at("foo abc\n", multiline = false)))
    }

    private fun key(context: dev.ksurdy.claudeautocomplete.backend.CompletionContext = testContext(), config: BackendConfig = BackendConfig(ProviderKind.Claude, testConfig(), testCodexConfig())) =
        CompletionCache.key(context, config)

    @Test
    fun `key depends on prefix and suffix`() {
        val base = key(testContext(prefix = "a", suffix = "b"))
        assertEquals(base, key(testContext(prefix = "a", suffix = "b")))
        assertNotEquals(base, key(testContext(prefix = "aa", suffix = "b")))
        assertNotEquals(base, key(testContext(prefix = "a", suffix = "bb")))
    }

    @Test
    fun `key depends on context attributes`() {
        val base = key()
        assertNotEquals(base, key(testContext(filePath = "other.php")))
        assertNotEquals(base, key(testContext(languageId = "JS")))
        assertNotEquals(base, key(testContext(maxLines = 3)))
        assertNotEquals(base, key(testContext(multiline = true)))
    }

    @Test
    fun `key depends on config attributes`() {
        val base = key()
        assertNotEquals(base, key(config = claude(testConfig(model = "sonnet"))))
        assertNotEquals(base, key(config = claude(testConfig(effort = "high"))))
        assertNotEquals(base, key(config = claude(testConfig(customInstructions = "PSR-12"))))
    }

    @Test
    fun `key ignores far away prefix text`() {
        val tail = "t".repeat(600)
        assertEquals(key(testContext(prefix = "AAA$tail")), key(testContext(prefix = "BBB$tail")))
    }

    @Test
    fun `entries expire after ttl`() {
        var now = 0L
        val cache = CompletionCache(2, ttlMs = 60_000, clock = { now })
        cache.put(at("a"), "1")
        now = 59_999
        assertEquals("1", cache.get(at("a")))
        now = 60_001
        assertNull(cache.get(at("a")))
    }

    @Test
    fun `key depends on imported classes`() {
        val withImport = testContext(importedClasses = listOf(dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet("U.php", "PHP", "class U")))
        assertNotEquals(key(), key(withImport))
        assertNotEquals(
            key(withImport),
            key(testContext(importedClasses = listOf(dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet("U.php", "PHP", "class U2")))),
        )
    }

    private fun claude(config: dev.ksurdy.claudeautocomplete.backend.ClaudeConfig) = BackendConfig(ProviderKind.Claude, config, testCodexConfig())

    @Test
    fun `key depends on provider and its own settings only`() {
        val claudeKey = key()
        val codex = BackendConfig(ProviderKind.Codex, testConfig(), testCodexConfig())
        assertNotEquals(claudeKey, key(config = codex))
        assertNotEquals(key(config = codex), key(config = codex.copy(codex = testCodexConfig(model = "other"))))
        assertNotEquals(key(config = codex), key(config = codex.copy(codex = testCodexConfig(reasoningEffort = "high"))))
        assertEquals(key(config = codex), key(config = codex.copy(claude = testConfig(model = "sonnet"))))
        assertEquals(claudeKey, key(config = BackendConfig(ProviderKind.Claude, testConfig(), testCodexConfig(model = "other"))))
    }

    @Test
    fun `a single line suggestion is never served to a multi line request`() {
        val cache = CompletionCache(4)
        cache.put(at("public function archiveAction", "\n}", multiline = false), "(Request \$request): Response")
        assertNull(cache.get(at("public function archiveAction(", "\n}", multiline = true)))
        assertEquals("Request \$request): Response", cache.get(at("public function archiveAction(", "\n}", multiline = false)))
    }

    @Test
    fun `a multi line suggestion is served to a multi line request that typed its start`() {
        val cache = CompletionCache(4)
        cache.put(at("    ", "\n}", multiline = true), "public function a(): int\n    {\n    }")
        assertEquals("): int\n    {\n    }", cache.get(at("    public function a(", "\n}", multiline = true)))
    }
}
