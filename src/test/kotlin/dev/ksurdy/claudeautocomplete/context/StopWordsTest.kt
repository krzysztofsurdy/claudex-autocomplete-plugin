package dev.ksurdy.claudeautocomplete.context

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StopWordsTest {
    @Test
    fun `every language gets the generic words`() {
        listOf("PHP", "Python", "kotlin", "TEXT", "Unknown").forEach {
            assertTrue("this" in StopWords.forLanguage(it), it)
            assertTrue("null" in StopWords.forLanguage(it), it)
        }
    }

    @Test
    fun `c style languages get their keywords`() {
        val php = StopWords.forLanguage("PHP")
        assertTrue("public" in php)
        assertTrue("function" in php)
        assertFalse("lambda" in php)
        assertTrue("fun" in StopWords.forLanguage("kotlin"))
    }

    @Test
    fun `scripting languages get their keywords`() {
        val python = StopWords.forLanguage("Python")
        assertTrue("def" in python)
        assertTrue("lambda" in python)
        assertFalse("public" in python)
        assertTrue("elsif" in StopWords.forLanguage("Ruby"))
    }

    @Test
    fun `unknown languages fall back to the c style set and sets are shared`() {
        assertSame(StopWords.forLanguage("PHP"), StopWords.forLanguage("Unknown"))
        assertSame(StopWords.forLanguage("python"), StopWords.forLanguage("PYTHON"))
    }
}
