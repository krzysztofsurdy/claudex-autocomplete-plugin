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
    @Test
    fun `stores and returns values`() {
        val cache = CompletionCache(2)
        cache.put("a", "1")
        assertEquals("1", cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `evicts least recently used`() {
        val cache = CompletionCache(2)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.get("a")
        cache.put("c", "3")
        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
        assertEquals("3", cache.get("c"))
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
        cache.put("a", "1")
        now = 59_999
        assertEquals("1", cache.get("a"))
        now = 60_001
        assertNull(cache.get("a"))
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
}
