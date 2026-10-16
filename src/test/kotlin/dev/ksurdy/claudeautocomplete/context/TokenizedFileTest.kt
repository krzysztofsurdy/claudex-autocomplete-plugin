package dev.ksurdy.claudeautocomplete.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizedFileTest {
    private val none = emptySet<String>()

    @Test
    fun `counts lines without a trailing empty line`() {
        assertEquals(2, TokenizedFile.of("alpha\nbeta\n", none, none).lineCount)
        assertEquals(3, TokenizedFile.of("alpha\n\nbeta", none, none).lineCount)
        assertEquals(0, TokenizedFile.of("", none, none).lineCount)
    }

    @Test
    fun `records tokens per line and skips stop words`() {
        val file = TokenizedFile.of("alpha beta\nthe gamma\n\ndelta", setOf("the"), none)
        assertEquals(4, file.tokenCount)
        assertEquals(listOf(0, 2, 3, 3, 4), file.lineTokenStart.toList())
    }

    @Test
    fun `same identifier shares one id and query flags follow the query set`() {
        val file = TokenizedFile.of("alpha beta alpha", none, setOf("beta"))
        assertEquals(2, file.vocabularySize)
        assertEquals(file.tokens[0], file.tokens[2])
        assertEquals(listOf(false, true), file.isQueryToken.toList())
    }

    @Test
    fun `slice returns whole lines without line breaks at the ends`() {
        val file = TokenizedFile.of("one\r\ntwo\r\nthree\r\n", none, none)
        assertEquals("one\r\ntwo", file.slice(0, 2))
        assertEquals("three", file.slice(2, 3))
        assertEquals("one\r\ntwo".length, file.length(0, 2))
    }

    @Test
    fun `reads only the first lines of a huge file`() {
        val file = TokenizedFile.of("alpha beta\n".repeat(TokenizedFile.MAX_LINES * 4), none, none)
        assertEquals(TokenizedFile.MAX_LINES, file.lineCount)
        assertEquals(TokenizedFile.MAX_LINES * 2, file.tokenCount)
    }

    @Test
    fun `reads only the start of a very long line`() {
        val file = TokenizedFile.of("ab ".repeat(100_000), none, none)
        assertTrue(file.tokenCount <= TokenizedFile.MAX_LINE_CHARS)
        assertEquals(1, file.lineCount)
    }
}
