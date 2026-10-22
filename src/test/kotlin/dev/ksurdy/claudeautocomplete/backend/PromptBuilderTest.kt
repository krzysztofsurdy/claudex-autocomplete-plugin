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
    fun `empty body mode asks for the complete body`() {
        val message = PromptBuilder.userMessage(testContext(multiline = true, maxLines = 20, emptyBody = true))
        assertTrue(message.endsWith("<mode>body (max 20 lines)</mode>"))
    }

    @Test
    fun `empty body is ignored in single line mode`() {
        assertTrue(PromptBuilder.userMessage(testContext(multiline = false, emptyBody = true)).endsWith("<mode>single-line</mode>"))
    }

    @Test
    fun `system prompt explains body mode and shows a multi statement example`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "<mode>body (max N lines)</mode>")
        assertContains(system, "Do not stop after the first statement")
        assertContains(system, "<mode>body (max 20 lines)</mode>\n=> ")
    }

    @Test
    fun `alternative requests list the suggestions already shown`() {
        val message = PromptBuilder.userMessage(testContext(alternativeTo = listOf("foo()", "bar()")))
        assertContains(message, "<shown_suggestions>\n<suggestion>foo()</suggestion>\n<suggestion>bar()</suggestion>\n</shown_suggestions>\n")
        assertTrue(message.indexOf("</file>") < message.indexOf("<shown_suggestions>"))
        assertTrue(message.endsWith("<mode>single-line</mode>"))
        assertFalse(PromptBuilder.userMessage(testContext()).contains("<shown_suggestions>"))
    }

    @Test
    fun `retry requests tell the model its last reply repeated a shown suggestion`() {
        val message = PromptBuilder.userMessage(testContext(alternativeTo = listOf("foo()")).copy(distinctRetry = true))
        assertContains(message, "<retry>Your previous reply was identical to a shown suggestion.")
        assertTrue(message.indexOf("</shown_suggestions>") < message.indexOf("<retry>"))
        assertTrue(message.endsWith("<mode>single-line</mode>"))
        assertFalse(PromptBuilder.userMessage(testContext(alternativeTo = listOf("foo()"))).contains("<retry>"))
    }

    @Test
    fun `system prompt explains the retry note`() {
        assertContains(PromptBuilder.systemPrompt(""), "<retry> means your previous reply repeated a shown suggestion")
    }

    @Test
    fun `system prompt asks for a different suggestion when some were shown`() {
        assertContains(PromptBuilder.systemPrompt(""), "<shown_suggestions> lists suggestions the user already rejected")
    }

    @Test
    fun `system prompt explains mid line insertions`() {
        assertContains(PromptBuilder.systemPrompt(""), "When code follows the cursor on the same line, output only what belongs between the cursor and that code")
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
    fun `system prompt requires the insertion inside completion tags`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "<completion>")
        assertContains(system, "</completion>")
        assertContains(system, "ignored")
        assertContains(system, "<completion></completion>")
    }

    @Test
    fun `system prompt examples wrap the reply in completion tags`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "=> <completion>findOneBy(['email' => \$email])</completion>")
        assertContains(system, "<mode>multi-line (max 12 lines)</mode>\n=> <completion>return a + b;</completion>")
        assertContains(system, "<mode>body (max 20 lines)</mode>\n=> <completion>const response = await fetch")
        assertContains(system, "return response.json();</completion>")
    }

    @Test
    fun `system prompt reserves the empty completion for cases where nothing fits`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "almost never right on a blank line inside a class, interface, trait or function body")
        assertContains(system, "propose the most likely next member or statement, matching the neighbouring code")
        assertContains(system, "<completion></completion>")
    }

    @Test
    fun `system prompt shows a blank line between methods producing a full method`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "public function count(): int")
        assertContains(system, "<mode>multi-line (max 20 lines)</mode>\n=> <completion>public function clear(): void\n    {")
        assertContains(system, "\$this->items = [];\n    }</completion>")
    }

    @Test
    fun `custom instructions come after the reply contract`() {
        val system = PromptBuilder.systemPrompt("Follow PSR-12")
        assertTrue(system.indexOf("<completion>") < system.indexOf("Follow PSR-12"))
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
