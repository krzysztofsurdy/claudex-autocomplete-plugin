package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamingCompletionTest {
    private val body = testContext(prefix = "function a() {\n", suffix = "\n}\n", multiline = true, maxLines = 30)

    @Test
    fun `nothing is shown before the first statement line is complete`() {
        val stream = StreamingCompletion(body)
        assertEquals("", stream.onPartial("\$a = 1"))
        assertEquals("", stream.onPartial("\$a = 1;"))
    }

    @Test
    fun `complete balanced lines are shown while the reply is still streaming`() {
        val stream = StreamingCompletion(body)
        assertEquals("\$a = 1;", stream.onPartial("\$a = 1;\n"))
        assertEquals("\n\$b = 2;", stream.onPartial("\$a = 1;\n\$b = 2;\n"))
        assertEquals("", stream.onPartial("\$a = 1;\n\$b = 2;\nreturn"))
        assertEquals("\nreturn \$a + \$b;", stream.onFinal("\$a = 1;\n\$b = 2;\nreturn \$a + \$b;"))
        assertEquals("\$a = 1;\n\$b = 2;\nreturn \$a + \$b;", stream.text)
    }

    @Test
    fun `an open block is held back until it closes`() {
        val stream = StreamingCompletion(body)
        assertEquals("", stream.onPartial("if (\$x) {\n    run();\n"))
        assertEquals("if (\$x) {\n    run();\n}", stream.onPartial("if (\$x) {\n    run();\n}\n"))
        assertEquals("\nnext();", stream.onPartial("if (\$x) {\n    run();\n}\nnext();\n"))
    }

    @Test
    fun `single line mode shows only the final text`() {
        val stream = StreamingCompletion(testContext(prefix = "\$x = ", multiline = false))
        assertEquals("", stream.onPartial("foo();\n"))
        assertEquals("foo();", stream.onFinal("foo();"))
    }

    @Test
    fun `final text that contradicts what was shown adds nothing`() {
        val stream = StreamingCompletion(body)
        stream.onPartial("a();\nb();\nc();\n")
        assertEquals("", stream.onFinal("x();"))
        assertEquals("a();\nb();\nc();", stream.text)
    }

    @Test
    fun `final text without partials is shown at once`() {
        val stream = StreamingCompletion(body)
        assertEquals("a();\nb();", stream.onFinal("a();\nb();"))
    }
}
