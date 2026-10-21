package dev.ksurdy.claudeautocomplete.completion

import kotlin.test.Test
import kotlin.test.assertEquals

class InFlightMatchTest {
    private val origin = InFlightOrigin("scope", "foo ", "\nend")

    private fun wanted(prefix: String, suffix: String = "\nend", scope: String = "scope") = InFlightOrigin(scope, prefix, suffix)

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
}
