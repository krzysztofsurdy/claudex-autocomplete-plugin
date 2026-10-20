package dev.ksurdy.claudeautocomplete.nextedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextEditParserTest {
    @Test
    fun `parses a single line edit`() {
        assertEquals(ParsedEdit(12, 12, "return \$bar;"), NextEditParser.parse("<edit start_line=\"12\" end_line=\"12\">return \$bar;</edit>"))
    }

    @Test
    fun `parses a multi line edit and strips the framing newlines`() {
        val raw = "<edit start_line=\"3\" end_line=\"4\">\nfirst\n    second\n</edit>"
        assertEquals(ParsedEdit(3, 4, "first\n    second"), NextEditParser.parse(raw))
    }

    @Test
    fun `none means no edit`() {
        assertNull(NextEditParser.parse("<none/>"))
        assertNull(NextEditParser.parse("<none />"))
    }

    @Test
    fun `empty or blank replies mean no edit`() {
        assertNull(NextEditParser.parse(""))
        assertNull(NextEditParser.parse("   \n"))
    }

    @Test
    fun `an empty replacement deletes the lines`() {
        assertEquals(ParsedEdit(7, 8, ""), NextEditParser.parse("<edit start_line=\"7\" end_line=\"8\"></edit>"))
    }

    @Test
    fun `tolerates chatter and code fences around the element`() {
        val raw = "Sure, here it is:\n```xml\n<edit start_line=\"2\" end_line=\"2\">x = 1;</edit>\n```\nDone."
        assertEquals(ParsedEdit(2, 2, "x = 1;"), NextEditParser.parse(raw))
    }

    @Test
    fun `tolerates single quotes spacing and a missing end line`() {
        assertEquals(ParsedEdit(4, 4, "a"), NextEditParser.parse("<edit  start_line = '4'  end_line = '4' >a</edit >"))
        assertEquals(ParsedEdit(4, 4, "a"), NextEditParser.parse("<edit start_line=\"4\">a</edit>"))
    }

    @Test
    fun `tolerates attributes in the other order`() {
        assertEquals(ParsedEdit(4, 5, "a"), NextEditParser.parse("<edit end_line=\"5\" start_line=\"4\">a</edit>"))
    }

    @Test
    fun `takes the first edit only`() {
        val raw = "<edit start_line=\"1\" end_line=\"1\">a</edit><edit start_line=\"9\" end_line=\"9\">b</edit>"
        assertEquals(ParsedEdit(1, 1, "a"), NextEditParser.parse(raw))
    }

    @Test
    fun `a truncated element is rejected`() {
        assertNull(NextEditParser.parse("<edit start_line=\"1\" end_line=\"1\">half"))
    }

    @Test
    fun `invalid line numbers are rejected`() {
        assertNull(NextEditParser.parse("<edit start_line=\"0\" end_line=\"1\">a</edit>"))
        assertNull(NextEditParser.parse("<edit start_line=\"5\" end_line=\"4\">a</edit>"))
        assertNull(NextEditParser.parse("<edit start_line=\"x\" end_line=\"4\">a</edit>"))
        assertNull(NextEditParser.parse("<edit>a</edit>"))
    }

    @Test
    fun `strips echoed line numbers when they match the range`() {
        val raw = "<edit start_line=\"10\" end_line=\"11\">10| a\n11|     b</edit>"
        assertEquals(ParsedEdit(10, 11, "a\n    b"), NextEditParser.parse(raw))
    }

    @Test
    fun `keeps text that only looks like a line number`() {
        val raw = "<edit start_line=\"10\" end_line=\"10\">3| a</edit>"
        assertEquals(ParsedEdit(10, 10, "3| a"), NextEditParser.parse(raw))
    }

    @Test
    fun `strips a code fence inside the replacement`() {
        val raw = "<edit start_line=\"2\" end_line=\"2\">\n```php\n\$a = 1;\n```\n</edit>"
        assertEquals(ParsedEdit(2, 2, "\$a = 1;"), NextEditParser.parse(raw))
    }

    @Test
    fun `none after an edit is ignored`() {
        assertEquals(ParsedEdit(1, 1, "a"), NextEditParser.parse("<edit start_line=\"1\" end_line=\"1\">a</edit><none/>"))
    }
}
