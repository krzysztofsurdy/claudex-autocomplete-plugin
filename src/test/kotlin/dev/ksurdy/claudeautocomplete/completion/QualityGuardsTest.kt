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

    @Test
    fun `a closer below the caret behind blank lines is not doubled`() {
        val context = testContext(prefix = "function a() {\n    ", suffix = "\n\n\n}\n", multiline = true, maxLines = 30)
        val processed = CompletionPostProcessor.interpret("<completion>run();\n}</completion>", context)
        assertEquals("run();", processed.text)
        assertEquals(listOf(Guard.Closers), processed.cuts.map { it.guard })
        assertEquals(2, processed.cuts.single().linesBefore)
        assertEquals(1, processed.cuts.single().linesAfter)
    }

    @Test
    fun `an else branch closes once against the file`() {
        val context = testContext(prefix = "if (a) {\n", suffix = "\n}\n", multiline = true, maxLines = 30)
        val text = CompletionPostProcessor.process("<completion>run();\n} else {\n    other();\n}</completion>", context)
        assertEquals("run();\n} else {\n    other();", text)
        assertEquals(1, ("if (a) {\n$text\n}\n").lines().count { it == "}" })
    }

    @Test
    fun `the closer of a completed block is kept`() {
        val context = testContext(prefix = "function a() {\n    ", suffix = "\n}\n", multiline = true, maxLines = 30)
        val raw = "if (\$x) {\n        run();\n    }"
        assertEquals(raw, CompletionPostProcessor.process("<completion>$raw</completion>", context))
    }

    @Test
    fun `a suggestion that repeats the next line is dropped and reported`() {
        val context = testContext(prefix = "function a() {\n    \$x = 1;\n    ", suffix = "\n    return \$x;\n}\n", multiline = false)
        val processed = CompletionPostProcessor.interpret("<completion>return \$x;</completion>", context)
        assertEquals("", processed.text)
        assertEquals(Guard.NextLine, processed.cuts.single().guard)
    }

    @Test
    fun `the last lines that repeat the file are removed`() {
        val context = testContext(prefix = "function a() {\n    ", suffix = "\n    return \$x;\n}\n", multiline = true, maxLines = 30)
        val text = CompletionPostProcessor.process("<completion>\$x = load();\nreturn \$x;</completion>", context)
        assertEquals("\$x = load();", text)
    }

    @Test
    fun `a similar line is not a duplicate`() {
        val context = testContext(prefix = "function a() {\n    ", suffix = "\n    return \$x;\n}\n", multiline = true, maxLines = 30)
        assertEquals("return \$y;", CompletionPostProcessor.process("<completion>return \$y;</completion>", context))
    }

    @Test
    fun `a streaming result keeps the guard cuts for the log`() {
        val stream = StreamingCompletion(body)
        stream.onFinal("<completion>" + "tick();\n".repeat(60) + "</completion>")
        assertEquals(listOf(Guard.Repetition), stream.processed.cuts.map { it.guard })
    }
}
