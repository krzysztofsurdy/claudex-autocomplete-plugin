package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
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

    @Test
    fun `key depends on prefix suffix and model`() {
        val base = CompletionCache.key(testContext(prefix = "a", suffix = "b"), "haiku")
        assertEquals(base, CompletionCache.key(testContext(prefix = "a", suffix = "b"), "haiku"))
        assertNotEquals(base, CompletionCache.key(testContext(prefix = "aa", suffix = "b"), "haiku"))
        assertNotEquals(base, CompletionCache.key(testContext(prefix = "a", suffix = "bb"), "haiku"))
        assertNotEquals(base, CompletionCache.key(testContext(prefix = "a", suffix = "b"), "sonnet"))
    }

    @Test
    fun `key depends on multiline mode`() {
        assertNotEquals(
            CompletionCache.key(testContext(multiline = true), "haiku"),
            CompletionCache.key(testContext(multiline = false), "haiku"),
        )
    }

    @Test
    fun `key ignores far away prefix text`() {
        val tail = "t".repeat(600)
        assertEquals(
            CompletionCache.key(testContext(prefix = "AAA$tail"), "haiku"),
            CompletionCache.key(testContext(prefix = "BBB$tail"), "haiku"),
        )
    }
}
