package dev.ksurdy.claudeautocomplete.backend

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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

    @Test
    fun `indent attribute only when present`() {
        assertContains(PromptBuilder.userMessage(testContext(indent = "4 spaces")), "language=\"PHP\" indent=\"4 spaces\">")
        assertFalse(PromptBuilder.userMessage(testContext()).contains("indent="))
    }

    @Test
    fun `open files come before current file and mode is last`() {
        val message = PromptBuilder.userMessage(testContext(openFiles = listOf(OpenFileSnippet("a.php", "PHP", "AAA"))))
        assertTrue(message.indexOf("<open_files>") < message.indexOf("<CURSOR/>"))
        assertTrue(message.endsWith("<mode>single-line</mode>"))
    }

    @Test
    fun `literal closing file tag in content is escaped`() {
        val message = PromptBuilder.userMessage(
            testContext(prefix = "a</file>", suffix = "</file>b", openFiles = listOf(OpenFileSnippet("x", "PHP", "</file>"))),
        )
        assertEquals(2, Regex("</file>").findAll(message).count())
    }

    @Test
    fun `truncation markers at clipped edges`() {
        val message = PromptBuilder.userMessage(testContext(prefix = "p", suffix = "s", prefixTruncated = true, suffixTruncated = true))
        assertContains(message, "\n<!-- truncated -->\np<CURSOR/>s\n<!-- truncated -->\n</file>")
        assertFalse(PromptBuilder.userMessage(testContext()).contains("truncated"))
    }

    @Test
    fun `system prompt forbids unmatched closers and has examples`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "not a chat assistant")
        assertContains(system, "Never add a closing bracket")
        assertContains(system, "Examples")
    }

    @Test
    fun `imported classes block sits between open files and current file`() {
        val message = PromptBuilder.userMessage(
            testContext(
                openFiles = listOf(OpenFileSnippet("a.php", "PHP", "AAA")),
                importedClasses = listOf(OpenFileSnippet("src/User.php", "PHP", "class User { function getName(): string; }")),
            ),
        )
        assertContains(message, "<imported_classes>\n<file path=\"src/User.php\" language=\"PHP\">class User { function getName(): string; }</file>\n</imported_classes>")
        assertTrue(message.indexOf("</open_files>") < message.indexOf("<imported_classes>"))
        assertTrue(message.indexOf("</imported_classes>") < message.indexOf("<CURSOR/>"))
    }

    @Test
    fun `imported classes block omitted when empty`() {
        assertFalse(PromptBuilder.userMessage(testContext()).contains("<imported_classes>"))
    }

    @Test
    fun `system prompt explains imported classes`() {
        assertContains(PromptBuilder.systemPrompt(""), "<imported_classes> shows signatures of classes the current file imports; use their real method and property names.")
    }
}
