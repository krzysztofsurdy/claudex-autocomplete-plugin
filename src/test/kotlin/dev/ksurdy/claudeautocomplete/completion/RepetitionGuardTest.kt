package dev.ksurdy.claudeautocomplete.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepetitionGuardTest {
    private fun cut(text: String): String? = RepetitionGuard.cut(text)

    @Test
    fun `a single identifier repeated many times is cut away`() {
        assertEquals("a();", cut("a();\nreturn " + "foo ".repeat(40)))
    }

    @Test
    fun `a loop on the only line leaves nothing`() {
        assertEquals("", cut("foo ".repeat(40)))
        assertEquals("", cut("foo, ".repeat(40)))
    }

    @Test
    fun `lines before a looping line are kept`() {
        val raw = "val x = compute()\nprintln(x)\n" + "println(x)\n".repeat(30)
        assertEquals("val x = compute()", cut(raw))
    }

    @Test
    fun `a looping statement of several tokens is cut`() {
        val raw = "a = 1;\n" + "    b = foo(c, d);\n".repeat(20)
        assertEquals("a = 1;", cut(raw))
    }

    @Test
    fun `a loop cut off in the middle of a pattern is removed completely`() {
        val raw = "run();\n" + "check(alpha, beta);\n".repeat(15) + "check(alp"
        assertEquals("run();", cut(raw))
    }

    @Test
    fun `a loop that starts mid line drops that partial line`() {
        val raw = "first();\nreturn " + "x + ".repeat(40)
        assertEquals("first();", cut(raw))
    }

    @Test
    fun `a loop whose indentation changes is found without whitespace tokens`() {
        val raw = "go();\n" + (0 until 30).joinToString("") { "${" ".repeat(it % 5)}step(a);\n" }
        assertEquals("go();", cut(raw))
    }

    @Test
    fun `a long run of one symbol is a loop`() {
        assertEquals("go();", cut("go();\nfoo(1" + ")".repeat(150)))
        assertEquals("", cut(")".repeat(150)))
    }

    @Test
    fun `a pattern of symbols is a loop`() {
        assertEquals("x();", cut("x();\n" + "{}".repeat(80)))
    }

    @Test
    fun `a short list with repeated items is kept`() {
        assertNull(cut("foo(a, a, a, a)"))
        assertNull(cut("foo(a, a, a, a, a, a, a, a"))
    }

    @Test
    fun `a list of zeros that is closed is kept`() {
        assertNull(cut("val zeros = [" + "0, ".repeat(16) + "0]"))
    }

    @Test
    fun `a banner line made of one symbol is kept`() {
        assertNull(cut("// " + "=".repeat(70)))
        assertNull(cut("x();\n// " + "-".repeat(80)))
    }

    @Test
    fun `repeated asserts that differ are kept`() {
        val raw = (1..12).joinToString("\n") { "assertEquals($it, compute($it));" }
        assertNull(cut(raw))
    }

    @Test
    fun `a handful of identical asserts is kept`() {
        assertNull(cut("assertTrue(ready);\n".repeat(6).trimEnd()))
        assertNull(cut("    assertNotNull(subject);\n".repeat(5).trimEnd()))
    }

    @Test
    fun `identical closing braces are kept`() {
        assertNull(cut("}\n".repeat(6).trimEnd()))
        assertNull(cut("    }\n".repeat(6) + "}"))
    }

    @Test
    fun `plain code without repetition is kept`() {
        assertNull(cut("if (a) {\n    run(a, b);\n} else {\n    stop();\n}"))
        assertNull(cut(""))
    }

    @Test
    fun `a tail that stopped repeating before the end is kept`() {
        assertNull(cut("foo ".repeat(40) + "bar"))
    }

    @Test
    fun `unicode identifiers and numbers are tokenised`() {
        assertEquals("", cut("wert 1.5 ".repeat(30)))
        assertEquals("", cut("größe ".repeat(40)))
    }

    @Test
    fun `hold back start reports a looser loop than the cut does`() {
        val partial = "setup();\n" + "step(a);\n".repeat(5)
        assertNull(RepetitionGuard.cut(partial))
        assertEquals("setup();", RepetitionGuard.holdBack(partial))
    }

    @Test
    fun `hold back keeps text without a loop`() {
        assertEquals("a();\nb();\nc();", RepetitionGuard.holdBack("a();\nb();\nc();"))
    }
}
