package dev.ksurdy.claudeautocomplete.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypedTrimTest {
    @Test
    fun `nothing typed passes the text through`() {
        val trim = TypedTrim("")
        assertEquals("ab", trim.advance("ab"))
        assertEquals("cd", trim.advance("abcd"))
        assertEquals("", trim.advance("abcd"))
        assertFalse(trim.diverged)
    }

    @Test
    fun `the typed start is cut off and only the rest is returned`() {
        val trim = TypedTrim("ab")
        assertEquals("", trim.advance("a"))
        assertEquals("", trim.advance("ab"))
        assertEquals("cd", trim.advance("abcd"))
        assertEquals("ef", trim.advance("abcdef"))
        assertFalse(trim.diverged)
    }

    @Test
    fun `text that departs from the typed start diverges and returns nothing`() {
        val trim = TypedTrim("ab")
        assertEquals("", trim.advance("ax"))
        assertTrue(trim.diverged)
        assertEquals("", trim.advance("axyz"))
    }

    @Test
    fun `text longer than the typed start that does not begin with it diverges`() {
        val trim = TypedTrim("ab")
        assertEquals("", trim.advance("xbcd"))
        assertTrue(trim.diverged)
    }

    @Test
    fun `the rest of the final text is whole when it arrives at once`() {
        val trim = TypedTrim("wo")
        assertEquals("rld", trim.advance("world"))
    }

    @Test
    fun `reports whether the text has outgrown the typed start`() {
        val trim = TypedTrim("ab")
        trim.advance("ab")
        assertFalse(trim.hasRest)
        trim.advance("abc")
        assertTrue(trim.hasRest)
    }
}
