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
        for (p in listOf("if (a) {", "[", "def f():", "x => {", "x = [  ")) {
            assertTrue(TriggerRules.isMultiline(p, "", "auto"), p)
        }
    }

    @Test
    fun `auto non openers are single line`() {
        for (p in listOf("foo(", "\$a->", "Foo::", "x =>", "public function add(int \$a): int", "\$x = \$a + ", "return \$this->na")) {
            assertFalse(TriggerRules.isMultiline(p, "", "auto"), p)
        }
    }

    @Test
    fun `auto opener needs caret at end of line`() {
        assertFalse(TriggerRules.isMultiline("if (a) {", "foo();", "auto"))
        assertTrue(TriggerRules.isMultiline("if (a) {", "}", "auto"))
    }
}
