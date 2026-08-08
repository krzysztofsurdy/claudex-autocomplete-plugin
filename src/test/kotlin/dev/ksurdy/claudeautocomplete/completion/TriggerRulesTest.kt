package dev.ksurdy.claudeautocomplete.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerRulesTest {
    @Test
    fun `triggers at end of line`() = assertTrue(TriggerRules.shouldTrigger("foo(", ""))

    @Test
    fun `triggers when rest of line is closing chars`() {
        assertTrue(TriggerRules.shouldTrigger("foo(", ")"))
        assertTrue(TriggerRules.shouldTrigger("foo(", "]);\nnext"))
        assertTrue(TriggerRules.shouldTrigger("a = \"", "\";"))
        assertTrue(TriggerRules.shouldTrigger("foo(", "  )  \nbar"))
    }

    @Test
    fun `does not trigger with code right of caret`() {
        assertFalse(TriggerRules.shouldTrigger("foo(", "\$a, \$b)"))
        assertFalse(TriggerRules.shouldTrigger("\$x = ", "bar();"))
    }

    @Test
    fun `only the caret line matters`() = assertTrue(TriggerRules.shouldTrigger("foo", "\nbar();"))

    @Test
    fun `mode always and never`() {
        assertTrue(TriggerRules.isMultiline("x = 1", "", "always"))
        assertFalse(TriggerRules.isMultiline("function a() {", "", "never"))
    }

    @Test
    fun `auto blank caret line is multiline`() {
        assertTrue(TriggerRules.isMultiline("a();\n", "", "auto"))
        assertTrue(TriggerRules.isMultiline("a();\n    ", "", "auto"))
    }

    @Test
    fun `auto block openers are multiline`() {
        for (p in listOf("if (a) {", "foo(", "[", "def f():", "x =>", "\$a->", "x = [  ")) {
            assertTrue(TriggerRules.isMultiline(p, "", "auto"), p)
        }
    }

    @Test
    fun `auto function signature is multiline`() {
        assertTrue(TriggerRules.isMultiline("public function add(int \$a): int", "", "auto"))
        assertTrue(TriggerRules.isMultiline("function add(\$a, \$b)", "", "auto"))
    }

    @Test
    fun `auto mid statement is single line`() {
        assertFalse(TriggerRules.isMultiline("\$x = \$a + ", "", "auto"))
        assertFalse(TriggerRules.isMultiline("return \$this->na", "", "auto"))
        assertEquals(false, TriggerRules.isMultiline("echo 'a'", "", "auto"))
    }
}
