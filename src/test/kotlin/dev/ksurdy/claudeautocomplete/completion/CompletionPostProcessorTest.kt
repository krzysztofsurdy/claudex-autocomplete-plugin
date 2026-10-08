package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionPostProcessorTest {
    private fun single(raw: String, prefix: String = "x = ", suffix: String = "") =
        CompletionPostProcessor.process(raw, testContext(prefix = prefix, suffix = suffix, multiline = false))

    private fun multi(raw: String, prefix: String = "if (a) {\n", suffix: String = "", maxLines: Int = 12) =
        CompletionPostProcessor.process(raw, testContext(prefix = prefix, suffix = suffix, multiline = true, maxLines = maxLines))

    @Test
    fun `strips markdown fences with language`() {
        assertEquals("foo();\nbar();", multi("```php\nfoo();\nbar();\n```"))
    }

    @Test
    fun `strips bare fences`() {
        assertEquals("foo();", multi("```\nfoo();\n```"))
    }

    @Test
    fun `single line truncates at first newline`() {
        assertEquals("1 + 2;", single("1 + 2;\n\$y = 3;"))
    }

    @Test
    fun `single line drops leading blank lines`() {
        assertEquals("1 + 2;", single("\n1 + 2;\nmore"))
    }

    @Test
    fun `multi line is limited to max lines`() {
        assertEquals("a\nb", multi("a\nb\nc\nd", maxLines = 2))
    }

    @Test
    fun `trailing whitespace is removed`() {
        assertEquals("foo();", multi("foo();\n\n  \n"))
    }

    @Test
    fun `echoed current line prefix is removed`() {
        assertEquals("42;", single("x = 42;", prefix = "<?php\nx = "))
    }

    @Test
    fun `echoed indentation only is removed`() {
        assertEquals("return 1;", multi("    return 1;", prefix = "function a() {\n    "))
    }

    @Test
    fun `tail overlapping suffix is trimmed`() {
        assertEquals("\$a, \$b", single("\$a, \$b)", prefix = "add(", suffix = ")"))
    }

    @Test
    fun `tail overlap across closing sequence is trimmed`() {
        assertEquals("1", single("1);", prefix = "foo(", suffix = ");"))
    }

    @Test
    fun `no overlap leaves text untouched`() {
        assertEquals("abc", single("abc", prefix = "p", suffix = ")"))
    }

    @Test
    fun `completion entirely overlapping suffix becomes empty`() {
        assertEquals("", single(")", prefix = "foo(", suffix = ")"))
    }

    @Test
    fun `blank raw yields empty`() {
        assertEquals("", single("   \n"))
    }

    @Test
    fun `multi line tail overlap with following lines trimmed`() {
        assertEquals("return 1;", multi("return 1;\n}", prefix = "function a() {\n    ", suffix = "\n}\n"))
    }

    @Test
    fun `closing brace of own block is kept when suffix starts with brace`() {
        val raw = "if (\$x) {\n        run();\n    }"
        assertEquals(raw, multi(raw, prefix = "function a() {\n    ", suffix = "\n}\n"))
    }

    @Test
    fun `only unmatched closers are trimmed`() {
        assertEquals("foo(1)", single("foo(1))", prefix = "bar(", suffix = "))"))
    }

    @Test
    fun `brackets inside strings are ignored for balance`() {
        assertEquals("\"a)\"", single("\"a)\")", prefix = "bar(", suffix = ")"))
    }

    @Test
    fun `partial echo of at least two chars is removed`() {
        assertEquals("ers", single("users", prefix = "\$this->us"))
    }

    @Test
    fun `single char overlap is not treated as echo`() {
        assertEquals("(1)", single("(1)", prefix = "foo("))
    }

    @Test
    fun `echo is compared ignoring leading whitespace of line prefix`() {
        assertEquals("= 5;", single("\$a = 5;", prefix = "    \$a "))
    }

    @Test
    fun `blank caret line strips all leading newlines`() {
        assertEquals("return 1;", multi("\n\nreturn 1;", prefix = "function a() {\n"))
    }

    @Test
    fun `blank caret line drops echoed indentation that follows a leading newline`() {
        assertEquals("return 1;\n    return 2;", multi("\n    return 1;\n    return 2;", prefix = "function a() {\n    "))
    }

    @Test
    fun `first line starts at the caret on an indented blank line whatever indent the model adds`() {
        assertEquals("\$form = 1;\n        return 2;", multi("       \$form = 1;\n        return 2;", prefix = "    function a() {\n        "))
        assertEquals("\$form = 1;", multi("               \$form = 1;", prefix = "    function a() {\n        "))
    }

    @Test
    fun `non blank caret line keeps one leading newline`() {
        assertEquals("\nreturn 1;", multi("\n\nreturn 1;", prefix = "function a() {"))
    }

    @Test
    fun `over long output is cut at the last balanced line instead of inside a block`() {
        val raw = "a();\n\nif (\$x) {\n    b();\n    c();\n}\nd();"
        assertEquals("a();", multi(raw, maxLines = 5))
        assertEquals("a();\n\nif (\$x) {\n    b();\n    c();\n}", multi(raw, maxLines = 6))
    }

    @Test
    fun `over long output without a balanced line falls back to a hard cut`() {
        assertEquals("if (\$x) {\n    b();", multi("if (\$x) {\n    b();\n    c();\n}", maxLines = 2))
    }

    @Test
    fun `brackets in strings and line comments do not affect the balanced cut`() {
        val raw = "a('(');\n// don't stop {\nb();\nif (\$x) {\n    c();\n}"
        assertEquals("a('(');\n// don't stop {\nb();", multi(raw, maxLines = 4))
    }

    @Test
    fun `output within the limit is not cut`() {
        val raw = "if (\$x) {\n    b();\n}"
        assertEquals(raw, multi(raw, maxLines = 3))
    }

    @Test
    fun `continuation lines with relative indentation are shifted to the caret indent`() {
        val raw = "\$a = 1;\n\nif (\$a) {\n    run();\n}"
        val expected = "\$a = 1;\n\n        if (\$a) {\n            run();\n        }"
        assertEquals(expected, multi(raw, prefix = "    function a() {\n        ", suffix = "\n    }\n"))
    }

    @Test
    fun `continuation lines already at the caret indent are untouched`() {
        val raw = "\$a = 1;\n        return \$a;"
        assertEquals(raw, multi(raw, prefix = "    function a() {\n        ", suffix = "\n    }\n"))
    }

    @Test
    fun `tab indentation is shifted with tabs`() {
        assertEquals("a();\n\t\tb();", multi("a();\nb();", prefix = "\tfunction a() {\n\t\t", suffix = "\n\t}\n"))
    }

    @Test
    fun `indentation is not shifted when the caret line has code`() {
        assertEquals("\nb();", multi("\nb();", prefix = "    if (a) {"))
    }
}
