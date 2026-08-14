package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextLimitsTest {
    @Test
    fun tailKeepsLastCharacters() {
        assertEquals("def", ContextLimits.tail("abcdef", 3))
        assertEquals("abc", ContextLimits.tail("abc", 10))
    }

    @Test
    fun headKeepsFirstCharacters() {
        assertEquals("abc", ContextLimits.head("abcdef", 3))
        assertEquals("abc", ContextLimits.head("abc", 10))
    }

    @Test
    fun budgetSplitsEvenlyAcrossSnippets() {
        val snippets = listOf(
            OpenFileSnippet("a", "PHP", "x".repeat(100)),
            OpenFileSnippet("b", "PHP", "y".repeat(100)),
        )
        val result = ContextLimits.budget(snippets, 100)
        assertEquals(listOf(50, 50), result.map { it.content.length })
    }

    @Test
    fun budgetRedistributesUnusedShareToLaterSnippets() {
        val snippets = listOf(
            OpenFileSnippet("a", "PHP", "x".repeat(10)),
            OpenFileSnippet("b", "PHP", "y".repeat(500)),
        )
        val result = ContextLimits.budget(snippets, 100)
        assertEquals(listOf(10, 90), result.map { it.content.length })
    }

    @Test
    fun budgetDropsEmptyAndHandlesZeroBudget() {
        assertTrue(ContextLimits.budget(listOf(OpenFileSnippet("a", "PHP", "abc")), 0).isEmpty())
        assertTrue(ContextLimits.budget(listOf(OpenFileSnippet("a", "PHP", "")), 100).isEmpty())
    }

    @Test
    fun disabledLanguagesMatchCaseInsensitively() {
        assertTrue(ContextLimits.isLanguageDisabled("php", " PHP , JSON"))
        assertTrue(ContextLimits.isLanguageDisabled("JSON", "php,json"))
        assertTrue(!ContextLimits.isLanguageDisabled("Kotlin", "php,json"))
        assertTrue(!ContextLimits.isLanguageDisabled("PHP", ""))
    }
}
