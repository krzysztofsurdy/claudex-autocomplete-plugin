package dev.ksurdy.claudeautocomplete.alternatives

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlternativeTextsTest {
    private val shown = listOf("foo(\$a, \$b);", "return 1")

    @Test
    fun `identical text is a duplicate`() {
        assertTrue(AlternativeTexts.isDuplicate("return 1", shown))
    }

    @Test
    fun `whitespace differences do not make a text distinct`() {
        assertTrue(AlternativeTexts.isDuplicate("  foo(\$a,\n    \$b);  ", shown))
        assertTrue(AlternativeTexts.isDuplicate("return   1", shown))
    }

    @Test
    fun `trailing punctuation does not make a text distinct`() {
        assertTrue(AlternativeTexts.isDuplicate("foo(\$a, \$b)", shown))
        assertTrue(AlternativeTexts.isDuplicate("return 1;", shown))
        assertTrue(AlternativeTexts.isDuplicate("return 1,", shown))
    }

    @Test
    fun `different code is distinct`() {
        assertFalse(AlternativeTexts.isDuplicate("return 2", shown))
        assertFalse(AlternativeTexts.isDuplicate("foo(\$b, \$a);", shown))
    }

    @Test
    fun `blank text is never reported as a duplicate of a blank shown text`() {
        assertFalse(AlternativeTexts.isDuplicate("", listOf("")))
    }

    @Test
    fun `a distinct first reply is used without a retry`() = runBlocking {
        val asked = mutableListOf<Boolean>()
        val text = AlternativeTexts.fetchDistinct(shown) { retry -> asked += retry; "return 2" }
        assertEquals("return 2", text)
        assertEquals(listOf(false), asked)
    }

    @Test
    fun `a duplicate reply is retried once with the stronger instruction`() = runBlocking {
        val replies = ArrayDeque(listOf("return 1;", "return 3"))
        val asked = mutableListOf<Boolean>()
        val text = AlternativeTexts.fetchDistinct(shown) { retry -> asked += retry; replies.removeFirst() }
        assertEquals("return 3", text)
        assertEquals(listOf(false, true), asked)
    }

    @Test
    fun `two duplicate replies give nothing new`() = runBlocking {
        val asked = mutableListOf<Boolean>()
        val text = AlternativeTexts.fetchDistinct(shown) { retry -> asked += retry; "return 1" }
        assertNull(text)
        assertEquals(listOf(false, true), asked)
    }

    @Test
    fun `a blank reply gives nothing new and is not retried`() = runBlocking {
        val asked = mutableListOf<Boolean>()
        val text = AlternativeTexts.fetchDistinct(shown) { retry -> asked += retry; "" }
        assertNull(text)
        assertEquals(listOf(false), asked)
    }

    @Test
    fun `variants put the new text first and keep the shown ones`() {
        assertEquals(listOf("new", "a", "b"), AlternativeTexts.variants("new", listOf("a", "b")))
        assertEquals(listOf("a", "b"), AlternativeTexts.variants(null, listOf("a", "b")))
    }
}
