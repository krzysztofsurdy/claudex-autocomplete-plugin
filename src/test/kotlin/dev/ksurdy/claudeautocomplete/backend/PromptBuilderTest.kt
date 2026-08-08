package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptBuilderTest {
    @Test
    fun `user message wraps prefix and suffix around cursor marker`() {
        val message = PromptBuilder.userMessage(testContext(prefix = "abc", suffix = "def"))
        assertContains(message, "<file path=\"src/Foo.php\" language=\"PHP\">\nabc<CURSOR/>def\n</file>")
    }

    @Test
    fun `single line mode is announced`() {
        assertContains(PromptBuilder.userMessage(testContext(multiline = false)), "<mode>single-line</mode>")
    }

    @Test
    fun `multi line mode announces max lines`() {
        val message = PromptBuilder.userMessage(testContext(multiline = true, maxLines = 7))
        assertContains(message, "<mode>multi-line (max 7 lines)</mode>")
    }

    @Test
    fun `open files block is omitted when there are none`() {
        assertFalse(PromptBuilder.userMessage(testContext()).contains("<open_files>"))
    }

    @Test
    fun `open files block lists each snippet`() {
        val message = PromptBuilder.userMessage(
            testContext(openFiles = listOf(OpenFileSnippet("a.php", "PHP", "AAA"), OpenFileSnippet("b.js", "JavaScript", "BBB"))),
        )
        assertContains(message, "<open_files>")
        assertContains(message, "<file path=\"a.php\" language=\"PHP\">AAA</file>")
        assertContains(message, "<file path=\"b.js\" language=\"JavaScript\">BBB</file>")
        assertContains(message, "</open_files>")
    }

    @Test
    fun `system prompt describes the job`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "<CURSOR/>")
        assertContains(system, "ONLY")
        assertContains(system, "markdown")
    }

    @Test
    fun `system prompt appends custom instructions only when present`() {
        assertContains(PromptBuilder.systemPrompt("Follow PSR-12"), "Follow PSR-12")
        assertTrue(PromptBuilder.systemPrompt("  ") == PromptBuilder.systemPrompt(""))
    }
}
