package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProseGuardTest {
    private fun rejects(raw: String, prefix: String = "    ", languageId: String = "PHP") =
        ProseGuard.rejects(raw, testContext(prefix = prefix, multiline = true, languageId = languageId))

    private fun trimmed(raw: String, prefix: String = "    ") =
        ProseGuard.withoutTrailingProse(raw, testContext(prefix = prefix, multiline = true))

    @Test
    fun `the real haiku replies are prose`() {
        assertTrue(rejects("The cursor sits inside `redirectToRoute(` with the `['id' => \$id]` argument already after it, so the insertion would be empty.\n\nHere is nothing."))
        assertTrue(rejects("Wait, that's not right. The cursor sits between two methods, so I should start a new one."))
    }

    @Test
    fun `chat openers are prose`() {
        assertTrue(rejects("Here is the completion:\n\$a = 1;"))
        assertTrue(rejects("Sure, here's the code you asked for."))
        assertTrue(rejects("Sure!"))
        assertTrue(rejects("I think the best fit is a return statement."))
        assertTrue(rejects("I'll complete the method call with the id."))
        assertTrue(rejects("Let me look at the surrounding code first."))
        assertTrue(rejects("Note: the cursor is inside a string."))
        assertTrue(rejects("This looks like a method call that needs an argument."))
        assertTrue(rejects("Looking at the file, the caret sits in a class body."))
        assertTrue(rejects("Okay, the next statement should return the value."))
    }

    @Test
    fun `a leading blank line or fence line does not hide prose`() {
        assertTrue(rejects("\n\nThe cursor sits in a call."))
        assertTrue(rejects("```\nThe cursor sits in a call.\n```"))
    }

    @Test
    fun `mentioning the cursor tag in a sentence is prose`() {
        assertTrue(rejects("Place <CURSOR/> after the opening brace of the method."))
        assertTrue(rejects("Nothing fits at <CURSOR/> here."))
    }

    @Test
    fun `a plain sentence with end punctuation and no code is prose`() {
        assertTrue(rejects("Nothing sensible can be inserted at this position."))
        assertTrue(rejects("There is no valid completion for this spot!"))
        assertTrue(rejects("What would you like me to write here?"))
    }

    @Test
    fun `code is never prose`() {
        assertFalse(rejects("return the_value;"))
        assertFalse(rejects("\$this->redirectToRoute('home', ['id' => \$id]);"))
        assertFalse(rejects("\$cursor = \$db->query(\$sql);"))
        assertFalse(rejects("cursor.execute(sql)"))
        assertFalse(rejects("Note::create(\$data);"))
        assertFalse(rejects("This->go();"))
        assertFalse(rejects("I = 3"))
        assertFalse(rejects("The::run()"))
        assertFalse(rejects("Here"))
        assertFalse(rejects("public function find(int \$id): ?User\n{\n}"))
        assertFalse(rejects("print(\"The cursor is here.\")"))
        assertFalse(rejects("x = \"Here is the answer.\""))
        assertFalse(rejects("DECLARE cursor CURSOR FOR"))
        assertFalse(rejects("end"))
        assertFalse(rejects("}"))
        assertFalse(rejects(""))
    }

    @Test
    fun `comments are code`() {
        assertFalse(rejects("// The cursor sits inside the call."))
        assertFalse(rejects("# Here is the explanation for the user."))
        assertFalse(rejects("/* Wait, that is not right. */"))
        assertFalse(rejects("/**\n * The cursor is a thing.\n */"))
        assertFalse(rejects("-- Note: this is a comment."))
        assertFalse(rejects("<!-- The cursor is here. -->"))
    }

    @Test
    fun `text typed inside a comment or string is not judged by its wording`() {
        assertFalse(rejects("The user identifier used for the lookup.", prefix = "    // "))
        assertFalse(rejects("The user identifier used for the lookup.", prefix = "    /**\n     * "))
        assertFalse(rejects("The user identifier used for the lookup.", prefix = "/* start\n"))
        assertFalse(rejects("The user identifier used for the lookup.", prefix = "\$a = \"Hello "))
        assertFalse(rejects("The user identifier used for the lookup.", prefix = "    # "))
        assertFalse(rejects("Here is the explanation for the reader.", prefix = "    \"\"\"\n    "))
        assertTrue(rejects("Place <CURSOR/> after the opening brace of the method.", prefix = "    // "))
    }

    @Test
    fun `markdown and plain text files are prose by nature`() {
        assertFalse(rejects("The cursor sits in a paragraph about editors.", languageId = "Markdown"))
        assertFalse(rejects("Here is how it works.", languageId = "TEXT"))
        assertFalse(rejects("Here is how it works.", languageId = "AsciiDoc"))
    }

    @Test
    fun `chat lines after the code are cut off`() {
        assertEquals(
            "\$learningTeamParticipantRepository->find(\$id);",
            trimmed("\$learningTeamParticipantRepository->find(\$id);\nWait, that's not right. The cursor sits between two methods.\n\npublic function x() {}"),
        )
        assertEquals("a();\nb();", trimmed("a();\nb();\nThe cursor is here, so I stop."))
        assertEquals("a();", trimmed("a();\nLet me reconsider this."))
    }

    @Test
    fun `code and comments after the first line are kept`() {
        val code = "a();\n// Wait, that is odd.\n\$b = \"Here is text.\";\nreturn Note::of(\$a);"
        assertEquals(code, trimmed(code))
    }

    @Test
    fun `docstring wording after the first line is kept`() {
        val code = "def f():\n    \"\"\"\n    This function returns one.\n    Note: it is cheap.\n    \"\"\"\n    return 1"
        assertEquals(code, trimmed(code))
    }

    @Test
    fun `text without prose is returned as is`() {
        assertEquals("a();\n\nb();", trimmed("a();\n\nb();"))
        assertEquals("", trimmed(""))
    }
}
