package dev.ksurdy.claudeautocomplete.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentifierTokenizerTest {
    private val none = emptySet<String>()

    @Test
    fun `extracts distinct identifiers and ignores punctuation`() {
        assertEquals(setOf("user", "getUser"), IdentifierTokenizer.tokens("\$user = getUser(\$user, 'x');", none))
    }

    @Test
    fun `drops single characters and tokens that start with a digit`() {
        assertTrue(IdentifierTokenizer.tokens("a b 9lives 0x1F 42 _", none).isEmpty())
        assertEquals(setOf("_x", "v2"), IdentifierTokenizer.tokens("_x v2", none))
    }

    @Test
    fun `keeps case and unicode letters`() {
        assertEquals(setOf("Foo", "foo", "größe"), IdentifierTokenizer.tokens("Foo foo größe", none))
    }

    @Test
    fun `drops stop words of the language`() {
        val php = StopWords.forLanguage("PHP")
        assertEquals(setOf("foo", "bar"), IdentifierTokenizer.tokens("public function foo() { return \$this->bar; }", php))
        val python = StopWords.forLanguage("Python")
        assertEquals(setOf("foo"), IdentifierTokenizer.tokens("def foo(self): return None", python))
    }

    @Test
    fun `scan reports token boundaries`() {
        val found = ArrayList<String>()
        val text = "ab, cd"
        IdentifierTokenizer.scan(text, 0, text.length) { start, end -> found += text.substring(start, end) }
        assertEquals(listOf("ab", "cd"), found)
    }

    @Test
    fun `scan stays inside the given range`() {
        val found = ArrayList<String>()
        val text = "aa bb cc"
        IdentifierTokenizer.scan(text, 3, 5) { start, end -> found += text.substring(start, end) }
        assertEquals(listOf("bb"), found)
    }
}
