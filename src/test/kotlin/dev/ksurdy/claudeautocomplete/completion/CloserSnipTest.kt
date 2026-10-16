package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CloserSnipTest {
    private fun snip(text: String, suffix: String, prefix: String = "function a() {\n    "): String? =
        CloserSnip.snip(text, testContext(prefix = prefix, suffix = suffix, multiline = true))

    @Test
    fun `a trailing closer that already exists below is removed`() {
        assertEquals("run();", snip("run();\n}", "\n}\n"))
    }

    @Test
    fun `blank lines between the caret and the closer are skipped`() {
        assertEquals("run();", snip("run();\n}", "\n\n\n}\n"))
    }

    @Test
    fun `blank lines inside the suggestion and a different indentation are ignored`() {
        assertEquals("run();", snip("run();\n\n        }\n", "\n}\n"))
    }

    @Test
    fun `several closers are removed together`() {
        assertEquals("x();", snip("x();\n}\n}", "\n}\n\n}\nfoo();"))
    }

    @Test
    fun `only as many closers as exist below are removed`() {
        assertEquals("x();\n}", snip("x();\n}\n}", "\n}\nfoo();"))
    }

    @Test
    fun `the closer of its own block is kept`() {
        assertNull(snip("if (\$x) {\n    run();\n}", "\n}\n"))
    }

    @Test
    fun `a balanced call keeps its closers`() {
        assertNull(snip("foo(function () {\n    x();\n});", "\n});\n"))
        assertNull(snip("a(\n    b()\n)", "\n)\n"))
    }

    @Test
    fun `closers with trailing punctuation are matched`() {
        assertEquals("x();", snip("x();\n});", "\n});\n"))
        assertEquals("x();", snip("x();\n}),", "\n}),\n"))
    }

    @Test
    fun `a closer of another kind is not removed`() {
        assertNull(snip("run();\n}", "\n)\n"))
        assertNull(snip("run();\n)", "\n}\n"))
    }

    @Test
    fun `a closer that matches an opener of the suggestion is kept even when more follow below`() {
        assertNull(snip("x(1\n)\n}", "\n)\n}\n"))
        assertNull(snip("x(\n    1\n)", "\n)\n}\n"))
    }

    @Test
    fun `unmatched closers of two kinds are removed together`() {
        assertEquals("run();", snip("run();\n)\n}", "\n)\n}\n"))
    }

    @Test
    fun `an else branch keeps its opening brace and drops the duplicate closer`() {
        assertEquals("run();\n} else {\n    other();", snip("run();\n} else {\n    other();\n}", "\n}\n"))
    }

    @Test
    fun `a last line that starts with the next line joins without a doubled brace`() {
        val text = "run();\n} else {"
        assertNull(snip(text, "\n}\n"))
        assertFalse(("    $text" + "\n}\n").contains("}\n}"))
    }

    @Test
    fun `an else line followed by its own closer is cut once`() {
        assertEquals("run();\n} else {", snip("run();\n} else {\n}", "\n}\n"))
    }

    @Test
    fun `end closers are removed when the suggestion did not open them`() {
        assertEquals("puts 1", snip("puts 1\nend", "\nend\n", prefix = "def a\n  "))
        assertEquals("x = 1", snip("x = 1\n  end", "\nend\nend\n", prefix = "def a\n  "))
    }

    @Test
    fun `an end that closes a block of the suggestion is kept`() {
        assertNull(snip("if x\n  y\nend", "\nend\n", prefix = "def a\n  "))
        assertNull(snip("items.each do |i|\n  p i\nend", "\nend\n", prefix = "def a\n  "))
        assertNull(snip("def b\n  1\nend", "\nend\n", prefix = "class A\n  "))
    }

    @Test
    fun `other closing keywords are removed too`() {
        assertEquals("run", snip("run\nfi", "\nfi\n", prefix = "if a; then\n  "))
        assertEquals("run", snip("run\ndone", "\ndone\n", prefix = "for a in b; do\n  "))
    }

    @Test
    fun `a suggestion that is only a closer becomes empty`() {
        assertEquals("", snip("}", "\n}\n", prefix = "function a() {\n"))
    }

    @Test
    fun `a first line after caret code is not a standalone closer`() {
        assertNull(snip(")", "\n)\n", prefix = "foo(1"))
    }

    @Test
    fun `nothing happens when code follows the caret on its line`() {
        assertNull(snip("run();\n}", "}\n}\n"))
    }

    @Test
    fun `a suffix without that closer changes nothing`() {
        assertNull(snip("run();\n}", "\nfoo();\n}\n"))
        assertNull(snip("run();\n}", ""))
    }

    @Test
    fun `a suggestion without closers changes nothing`() {
        assertNull(snip("run();\nnext();", "\n}\n"))
    }
}
