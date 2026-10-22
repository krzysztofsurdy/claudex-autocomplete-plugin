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

    @Test
    fun `a trailing duplicate closer never appears while streaming`() {
        val context = testContext(prefix = "function a() {\n", suffix = "\n\n}\n", multiline = true, maxLines = 400)
        val reply = "\$a = 1;\nreturn \$a;\n}"
        assertStable(reply, context)
        assertEquals("\$a = 1;\nreturn \$a;", run(reply, context).final)
    }

    @Test
    fun `an own closer streams as before`() {
        val context = testContext(prefix = "function a() {\n", suffix = "\n}\n", multiline = true, maxLines = 400)
        val reply = "if (\$x) {\n    run();\n}\nnext();"
        assertStable(reply, context)
        assertEquals(reply, run(reply, context).final)
    }

    @Test
    fun `lines that repeat the file below are held back until they diverge or end`() {
        val context = testContext(prefix = "function a() {\n", suffix = "\n    load();\n    save();\n}\n", multiline = true, maxLines = 400)
        val reply = "prepare();\nload();\nsave();"
        val run = run(reply, context)
        assertEquals("prepare();", run.streamed)
        assertEquals("prepare();", run.final)
        assertStable(reply, context)
    }

    @Test
    fun `a short line that only repeats the file together with the next one is held back`() {
        val context = testContext(prefix = "function a() {\n", suffix = "\n    i++;\n    save(\$x);\n}\n", multiline = true, maxLines = 400)
        val reply = "prepare();\ni++;\nsave(\$x);"
        val run = run(reply, context)
        assertEquals("prepare();", run.streamed)
        assertEquals("prepare();", run.final)
        assertStable(reply, context)
    }

    @Test
    fun `lines that start like the file below but go on differently are shown`() {
        val context = testContext(prefix = "function a() {\n", suffix = "\n    load();\n    save();\n}\n", multiline = true, maxLines = 400)
        val reply = "prepare();\nload();\nclose();"
        assertStable(reply, context)
        assertEquals(reply, run(reply, context).final)
    }

    @Test
    fun `an else branch streams without the doubled closer`() {
        val context = testContext(prefix = "if (a) {\n", suffix = "\n}\n", multiline = true, maxLines = 400)
        val reply = "run();\n} else {\n    other();\n}"
        assertStable(reply, context)
        assertEquals("run();\n} else {\n    other();", run(reply, context).final)
    }
}
