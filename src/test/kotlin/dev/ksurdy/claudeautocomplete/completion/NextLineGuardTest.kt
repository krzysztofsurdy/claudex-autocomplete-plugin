package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextLineGuardTest {
    private fun trim(text: String, prefix: String = "function a() {\n    ", suffix: String = "", multiline: Boolean = true): String? =
        NextLineGuard.trim(text, testContext(prefix = prefix, suffix = suffix, multiline = multiline))

    private fun pending(text: String, prefix: String = "function a() {\n    ", suffix: String = ""): String =
        NextLineGuard.holdBack(text, testContext(prefix = prefix, suffix = suffix, multiline = true))

    @Test
    fun `a single line equal to the next line is dropped`() {
        assertEquals("", trim("run();", suffix = "\n    run();\n}\n", multiline = false))
    }

    @Test
    fun `leading and trailing whitespace is ignored and blank lines are skipped`() {
        assertEquals("", trim("run();", suffix = "\n\n\n        run();   \n}\n"))
    }

    @Test
    fun `the caret line is part of the compared line`() {
        assertEquals("", trim("foo();", prefix = "    \$x = ", suffix = "\n    \$x = foo();\n", multiline = false))
        assertNull(trim("foo();", prefix = "    \$y = ", suffix = "\n    \$x = foo();\n", multiline = false))
    }

    @Test
    fun `a line that only differs is kept`() {
        assertNull(trim("run(1);", suffix = "\n    run(2);\n}\n"))
    }

    @Test
    fun `a suggestion before a mid line suffix is not compared`() {
        assertNull(trim("run();", prefix = "foo(", suffix = "run();\nrun();\n", multiline = false))
    }

    @Test
    fun `the last lines of a multi line suggestion are dropped`() {
        assertEquals("a();\nb();", trim("a();\nb();\nrun();", suffix = "\n    run();\n}\n"))
        assertEquals("first();", trim("first();\nsecond();\nthird();", suffix = "\n    second();\n    third();\n}\n"))
    }

    @Test
    fun `a multi line suggestion that repeats the next lines is dropped completely`() {
        assertEquals("", trim("load();\nsave();", suffix = "\nload();\n\nsave();\nclose();"))
    }

    @Test
    fun `lines in the middle are not dropped`() {
        assertNull(trim("run();\nb();", suffix = "\nrun();\n}\n"))
    }

    @Test
    fun `a match must start at the first line below the caret`() {
        assertNull(trim("load();\nsave();", suffix = "\nrun();\nsave();\n"))
    }

    @Test
    fun `closer lines are left to the closer snip`() {
        assertNull(trim("}", suffix = "\n}\n"))
        assertNull(trim("run();\n}", suffix = "\n}\n"))
    }

    @Test
    fun `a block that would be left open is not cut`() {
        val raw = "if (a) {\n    foo();\n}"
        assertNull(trim(raw, suffix = "\n    foo();\n}\n"))
    }

    @Test
    fun `a dropped tail that opens a block is cut`() {
        assertEquals("a();", trim("a();\nif (b) {\n    c();", suffix = "\nif (b) {\n    c();\n}\n"))
    }

    @Test
    fun `tiny lines are kept`() {
        assertNull(trim("i++;", suffix = "\n    i++;\n"))
        assertNull(trim("a;", suffix = "\n    a;\n"))
    }

    @Test
    fun `nothing below the caret changes nothing`() {
        assertNull(trim("run();", suffix = ""))
        assertNull(trim("run();", suffix = "\n\n  \n"))
    }

    @Test
    fun `hold back keeps the tail that could still turn into a duplicate`() {
        assertEquals("a();", pending("a();\nrun();", suffix = "\nrun();\nnext();\n}"))
        assertEquals("a();", pending("a();\nrun();\nnext();", suffix = "\nrun();\nnext();\n}"))
    }

    @Test
    fun `hold back leaves a tail that diverges`() {
        assertEquals("a();\nrun(2);", pending("a();\nrun(2);", suffix = "\nrun();\n}"))
    }

    @Test
    fun `hold back does not hold closers`() {
        assertEquals("if (a) {\n    run();\n}", pending("if (a) {\n    run();\n}", suffix = "\n}\n"))
    }
}
