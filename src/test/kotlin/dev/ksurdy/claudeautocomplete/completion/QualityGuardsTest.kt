package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityGuardsTest {
    private val body = testContext(prefix = "function a() {\n", suffix = "\n}\n", multiline = true, maxLines = 400)

    @Test
    fun `a looping reply is cut before the loop`() {
        val raw = "<completion>\$a = 1;\n" + "step(\$a);\n".repeat(60) + "</completion>"
        assertEquals("\$a = 1;", CompletionPostProcessor.process(raw, body))
    }

    @Test
    fun `a reply that is only a loop gives no suggestion`() {
        val raw = "<completion>" + "tick();\n".repeat(60) + "</completion>"
        val processed = CompletionPostProcessor.interpret(raw, body)
        assertEquals("", processed.text)
        assertEquals(listOf(Guard.Repetition), processed.cuts.map { it.guard })
        assertEquals(60, processed.cuts.single().linesBefore)
        assertEquals(0, processed.cuts.single().linesAfter)
    }

    @Test
    fun `a single line suggestion that loops is dropped`() {
        val context = testContext(prefix = "\$x = ", multiline = false)
        assertEquals("", CompletionPostProcessor.process("<completion>" + "foo + ".repeat(50) + "</completion>", context))
    }

    @Test
    fun `a clean reply reports no cut`() {
        val processed = CompletionPostProcessor.interpret("<completion>\$a = 1;\nreturn \$a;</completion>", body)
        assertTrue(processed.cuts.isEmpty())
        assertEquals("\$a = 1;\nreturn \$a;", processed.text)
    }

    @Test
    fun `a partial cut reports the line counts before and after`() {
        val raw = "<completion>\$a = 1;\n" + "step(\$a);\n".repeat(30) + "</completion>"
        val cut = CompletionPostProcessor.interpret(raw, body).cuts.single()
        assertEquals(Guard.Repetition, cut.guard)
        assertEquals(31, cut.linesBefore)
        assertEquals(1, cut.linesAfter)
    }

    @Test
    fun `the guard label names the cause for the log`() {
        assertEquals("repetition guard", Guard.Repetition.label)
    }
}
