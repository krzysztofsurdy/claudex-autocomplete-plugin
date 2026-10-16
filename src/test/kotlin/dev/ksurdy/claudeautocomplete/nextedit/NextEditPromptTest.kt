package dev.ksurdy.claudeautocomplete.nextedit

import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet
import dev.ksurdy.claudeautocomplete.backend.RelatedSnippet
import dev.ksurdy.claudeautocomplete.backend.PromptBuilder
import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NextEditPromptTest {
    private val input = NextEditInput(
        edits = listOf(EditHunk(2, "function foo(\$a)", "function foo(\$bar)"), EditHunk(9, "x", "y\nz")),
        windowText = "<?php\nfunction foo(\$bar)\n{\n    return \$a;\n}",
        firstLine = 1,
        caretLine = 2,
    )

    private val message = PromptBuilder.userMessage(testContext(indent = "4 spaces").copy(nextEdit = input))

    @Test
    fun `announces the mode last`() {
        assertTrue(message.endsWith("<mode>next-edit</mode>"), message)
    }

    @Test
    fun `lists recent edits as before and after hunks`() {
        assertContains(
            message,
            "<recent_edits>\n<edit line=\"2\">\n<before>\nfunction foo(\$a)\n</before>\n<after>\nfunction foo(\$bar)\n</after>\n</edit>\n",
        )
        assertContains(message, "<edit line=\"9\">\n<before>\nx\n</before>\n<after>\ny\nz\n</after>\n</edit>\n</recent_edits>")
    }

    @Test
    fun `numbers the file lines from the window start`() {
        assertContains(
            message,
            "<numbered_file path=\"src/Foo.php\" language=\"PHP\" indent=\"4 spaces\">\n1|<?php\n2|function foo(\$bar)\n3|{\n4|    return \$a;\n5|}\n</numbered_file>",
        )
    }

    @Test
    fun `windowed numbering starts at the first line`() {
        val windowed = PromptBuilder.userMessage(testContext().copy(nextEdit = input.copy(windowText = "a\nb", firstLine = 40)))
        assertContains(windowed, "40|a\n41|b\n")
    }

    @Test
    fun `reports the caret line`() {
        assertContains(message, "<caret line=\"2\"/>")
    }

    @Test
    fun `has no cursor marker or completion mode`() {
        assertFalse(message.contains("<CURSOR/>"))
        assertFalse(message.contains("single-line"))
    }

    @Test
    fun `does not send open tabs or imported classes`() {
        val withExtras = PromptBuilder.userMessage(
            testContext(relatedSnippets = listOf(RelatedSnippet("a", 1, 1, "AAA")), importedClasses = listOf(OpenFileSnippet("b", "PHP", "BBB")))
                .copy(nextEdit = input),
        )
        assertFalse(withExtras.contains("AAA"))
        assertFalse(withExtras.contains("BBB"))
    }

    @Test
    fun `closing tags in file content are escaped`() {
        val tricky = PromptBuilder.userMessage(testContext().copy(nextEdit = input.copy(windowText = "</numbered_file>\n</recent_edits>")))
        assertEquals(1, Regex("</numbered_file>").findAll(tricky).count())
        assertEquals(1, Regex("</recent_edits>").findAll(tricky).count())
    }

    @Test
    fun `system prompt describes the mode and the exact reply format`() {
        val system = PromptBuilder.systemPrompt("")
        assertContains(system, "<mode>next-edit</mode>")
        assertContains(system, "<edit start_line=\"N\" end_line=\"M\">")
        assertContains(system, "<none/>")
        assertContains(system, "<numbered_file>")
        assertContains(system, "<recent_edits>")
    }
}
