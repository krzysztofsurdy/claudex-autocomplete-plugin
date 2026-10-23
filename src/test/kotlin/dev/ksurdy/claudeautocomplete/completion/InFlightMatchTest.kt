package dev.ksurdy.claudeautocomplete.completion

import kotlin.test.Test
import kotlin.test.assertEquals

class InFlightMatchTest {
    private val origin = InFlightOrigin("scope", "foo ", "\nend", multiline = false)

    private fun wanted(prefix: String, suffix: String = "\nend", scope: String = "scope", multiline: Boolean = false) =
        InFlightOrigin(scope, prefix, suffix, multiline)

    @Test
    fun `typing that the streamed text starts with is reused`() {
        assertEquals(InFlightVerdict.Reuse("ab"), InFlightMatch.decide(origin, wanted("foo ab"), "abcdef"))
    }

    @Test
    fun `typing before anything streamed is reused`() {
        assertEquals(InFlightVerdict.Reuse("ab"), InFlightMatch.decide(origin, wanted("foo ab"), ""))
    }

    @Test
    fun `typing longer than the streamed text but consistent with it is reused`() {
        assertEquals(InFlightVerdict.Reuse("abcd"), InFlightMatch.decide(origin, wanted("foo abcd"), "ab"))
    }

    @Test
    fun `no typing is reused`() {
        assertEquals(InFlightVerdict.Reuse(""), InFlightMatch.decide(origin, wanted("foo "), "abc"))
    }

    @Test
    fun `typing that departs from the streamed text diverges`() {
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("foo ax"), "abcdef"))
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("foo x"), "abc"))
    }

    @Test
    fun `a changed suffix or scope diverges`() {
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("foo a", suffix = "\nend!"), "abc"))
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("foo a", scope = "other"), "abc"))
    }

    @Test
    fun `a prefix that does not extend the original diverges`() {
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("fo"), "abc"))
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("bar a"), "abc"))
    }

    @Test
    fun `a single line request is never reused for a multi line one`() {
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("foo a", multiline = true), "abc"))
        assertEquals(InFlightVerdict.Diverged, InFlightMatch.decide(origin, wanted("foo a", multiline = true), ""))
    }

    @Test
    fun `a multi line request is reused for either mode`() {
        val multi = InFlightOrigin("scope", "foo ", "\nend", multiline = true)
        assertEquals(InFlightVerdict.Reuse("a"), InFlightMatch.decide(multi, wanted("foo a", multiline = true), "abc"))
        assertEquals(InFlightVerdict.Reuse("a"), InFlightMatch.decide(multi, wanted("foo a", multiline = false), "abc"))
    }
}
