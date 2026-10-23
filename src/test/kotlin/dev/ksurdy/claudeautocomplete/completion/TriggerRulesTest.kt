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
    fun `triggers mid line when code right of caret starts after a separator`() {
        assertTrue(TriggerRules.shouldTrigger("foo(", "\$a, \$b)"))
        assertTrue(TriggerRules.shouldTrigger("\$x = ", "bar();"))
        assertTrue(TriggerRules.shouldTrigger("foo(\$a, ", " \$c)"))
    }

    @Test
    fun `does not trigger inside a word`() {
        assertFalse(TriggerRules.shouldTrigger("\$this->us", "ers;"))
        assertFalse(TriggerRules.shouldTrigger("fo", "o();"))
        assertFalse(TriggerRules.shouldTrigger("\$a", "\$b"))
    }

    @Test
    fun `auto mid line with code right of caret is single line`() {
        assertFalse(TriggerRules.isMultiline("if (a) {", "foo();", "auto"))
        assertFalse(TriggerRules.isMultiline("foo(", "\$b)", "auto"))
        assertFalse(TriggerRules.isMultiline("public function a(", "int \$id): Response", "auto"))
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
        for (p in listOf("if (a) {", "[", "def f():", "x => {", "x = [  ", "foo(", "    public function archiveAction(")) {
            assertTrue(TriggerRules.isMultiline(p, "", "auto"), p)
        }
    }

    @Test
    fun `auto non openers are single line`() {
        for (p in listOf("\$a->", "Foo::", "Foo::bar::", "x =>", "public function add(int \$a): int", "\$x = \$a + ", "return \$this->na")) {
            assertFalse(TriggerRules.isMultiline(p, "", "auto"), p)
        }
    }

    @Test
    fun `empty block body is detected`() {
        assertTrue(TriggerRules.isEmptyBody("    ): Response {\n        ", "\n    }\n}\n"))
        assertTrue(TriggerRules.isEmptyBody("function a() {\n", "}"))
        assertTrue(TriggerRules.isEmptyBody("function a() {", "}"))
    }

    @Test
    fun `block with existing statements is not an empty body`() {
        assertFalse(TriggerRules.isEmptyBody("function a() {\n    \$x = 1;\n    ", "\n}"))
        assertFalse(TriggerRules.isEmptyBody("function a() {\n    ", "\n    return 1;\n}"))
        assertFalse(TriggerRules.isEmptyBody("\$a = [\n    ", "\n];"))
    }

    @Test
    fun `auto opener needs caret at end of line`() {
        assertFalse(TriggerRules.isMultiline("if (a) {", "foo();", "auto"))
        assertTrue(TriggerRules.isMultiline("if (a) {", "}", "auto"))
    }

    @Test
    fun `open paren at the end of a line is multiline when the rest is empty or closers`() {
        assertTrue(TriggerRules.isMultiline("    public function archiveAction(", "\n\n    public function other()", "auto"))
        assertTrue(TriggerRules.isMultiline("    public function archiveAction(", ")\n}", "auto"))
        assertFalse(TriggerRules.isMultiline("    public function archiveAction(", "int \$id)", "never"))
    }

    @Test
    fun `open signature is a declaration`() {
        val prefixes = listOf(
            "class A\n{\n    public function archiveAction(",
            "    public static function create(",
            "    public function __construct(",
            "fun load(",
            "    private suspend fun load(",
            "def archive(",
            "func archive(",
            "export async function archive(",
            "    public void archive(",
            "    private static final String name(",
        )
        for (prefix in prefixes) {
            assertTrue(TriggerRules.isOpenDeclaration(prefix, "\n}\n"), prefix)
        }
    }

    @Test
    fun `calls are not declarations`() {
        val prefixes = listOf(
            "        return \$this->redirectToRoute(",
            "        \$items = array_map(function (",
            "        \$x = foo(",
            "        private \$x = foo(",
            "        if (",
            "        Foo::bar(",
            "        \$this->render(",
            "    public function archive(int \$id): Response",
            "    public function archive(int \$id,",
        )
        for (prefix in prefixes) {
            assertFalse(TriggerRules.isOpenDeclaration(prefix, "\n}\n"), prefix)
        }
    }

    @Test
    fun `declaration needs an empty rest of the line`() {
        assertFalse(TriggerRules.isOpenDeclaration("    public function archive(", ") {\n}"))
        assertFalse(TriggerRules.isOpenDeclaration("    public function archive(", "int \$id): Response"))
        assertTrue(TriggerRules.isOpenDeclaration("    public function archive(", "  \n}"))
        assertTrue(TriggerRules.isOpenDeclaration("    public function archive(", ""))
    }

    @Test
    fun `an auto inserted closing parenthesis keeps the declaration open`() {
        assertTrue(TriggerRules.isOpenDeclaration("    public function archive(", ")\n}"))
        assertTrue(TriggerRules.isOpenDeclaration("    public function archive(", ")  \n}"))
        assertTrue(TriggerRules.isOpenDeclaration("    public function archive(", ")"))
        assertFalse(TriggerRules.isOpenDeclaration("    public function archive(", " )\n}"))
        assertFalse(TriggerRules.isOpenDeclaration("    public function archive(", "))\n}"))
        assertFalse(TriggerRules.isOpenDeclaration("        \$x = foo(", ")\n}"))
    }

    @Test
    fun `the paired closer is reported only for a declaration`() {
        assertEquals(")", TriggerRules.pairedCloser("    public function archive(", ")\n}"))
        assertEquals("", TriggerRules.pairedCloser("    public function archive(", "\n}"))
        assertEquals("", TriggerRules.pairedCloser("        \$x = foo(", ")\n}"))
        assertEquals("", TriggerRules.pairedCloser("    public function archive(", ") {\n}"))
    }
}
