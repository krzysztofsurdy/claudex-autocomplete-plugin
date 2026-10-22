package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.testContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingGuardsTest {
    private val body = testContext(prefix = "function a() {\n", suffix = "\n}\n", multiline = true, maxLines = 400)

    private class Run(val shown: List<String>, val streamed: String, val final: String)

    private fun run(reply: String, context: CompletionContext): Run {
        val stream = StreamingCompletion(context)
        val shown = mutableListOf<String>()
        val block = "<completion>$reply</completion>"
        var cursor = 0
        while (true) {
            val next = block.indexOf('\n', cursor)
            if (next < 0) break
            shown += stream.onPartial(block.substring(0, next + 1))
            cursor = next + 1
        }
        val beforeFinal = stream.text
        shown += stream.onFinal(block)
        return Run(shown, beforeFinal, stream.text)
    }

    private fun assertStable(reply: String, context: CompletionContext) {
        val run = run(reply, context)
        val expected = CompletionPostProcessor.process("<completion>$reply</completion>", context)
        assertEquals(expected, run.final, "streamed text must equal the final-only text")
        assertTrue(expected.startsWith(run.streamed), "nothing shown before the end may be cut afterwards: '${run.streamed}' vs '$expected'")
        assertEquals(run.final, run.shown.joinToString(""))
    }

    @Test
    fun `a loop is held back once it repeats and shows at most its first line`() {
        val reply = "\$a = 1;\n" + "step(\$a);\n".repeat(60)
        val run = run(reply, body)
        assertEquals("\$a = 1;\nstep(\$a);", run.streamed)
        assertEquals(run.streamed, run.final)
    }

    @Test
    fun `a loop made of long lines is held back once it repeats`() {
        val line = "check(\$alpha, \$beta, \$gamma, \$delta, \$epsilon);"
        val run = run("\$a = 1;\n" + "$line\n".repeat(40), body)
        assertEquals("\$a = 1;\n$line", run.final)
        assertEquals(run.streamed, run.final)
    }

    @Test
    fun `plain code streams line by line as before`() {
        val reply = "\$a = 1;\n\$b = 2;\nreturn \$a + \$b;"
        val run = run(reply, body)
        assertEquals(listOf("\$a = 1;", "\n\$b = 2;", "\nreturn \$a + \$b;"), run.shown.filter { it.isNotEmpty() })
        assertStable(reply, body)
    }

    @Test
    fun `legit repeated lines are shown`() {
        val reply = (1..4).joinToString("\n") { "assertTrue(ready);" }
        assertStable(reply, body)
        assertEquals(reply, run(reply, body).final)
    }

    @Test
    fun `a bare reply streams through the guards too`() {
        val stream = StreamingCompletion(body)
        val raw = "\$a = 1;\n" + "step(\$a);\n".repeat(60)
        var cursor = 0
        while (true) {
            val next = raw.indexOf('\n', cursor)
            if (next < 0) break
            stream.onPartial(raw.substring(0, next + 1))
            cursor = next + 1
        }
        stream.onFinal(raw)
        assertEquals("\$a = 1;\nstep(\$a);", stream.text)
    }
}
