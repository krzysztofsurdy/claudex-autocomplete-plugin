package dev.ksurdy.claudeautocomplete.completion

import kotlin.test.Test
import kotlin.test.assertEquals

class CloserSpliceTest {
    private fun show(text: String) = SplicePiece.Show(text)

    private val skip = SplicePiece.Skip(")")

    @Test
    fun `splits the text at the closer of the signature`() {
        val splice = CloserSplice(")")
        assertEquals(
            listOf(show("Request \$request"), skip, show(": Response\n    {\n        return \$x;\n    }")),
            splice.pieces("Request \$request): Response\n    {\n        return \$x;\n    }"),
        )
    }

    @Test
    fun `a closer that closes an opener of the text is not the one to skip`() {
        val splice = CloserSplice(")")
        assertEquals(
            listOf(show("array \$a = [], int \$b = foo(1)"), skip, show(": int")),
            splice.pieces("array \$a = [], int \$b = foo(1)): int"),
        )
    }

    @Test
    fun `skips the closer once, also across chunks`() {
        val splice = CloserSplice(")")
        assertEquals(listOf(show("int \$a")), splice.pieces("int \$a"))
        assertEquals(listOf(skip, show(": int")), splice.pieces("): int"))
        assertEquals(listOf(show(" {}\n)")), splice.pieces(" {}\n)"))
    }

    @Test
    fun `a closer at the very start or end of a chunk`() {
        assertEquals(listOf(skip), CloserSplice(")").pieces(")"))
        assertEquals(listOf(show("a"), skip), CloserSplice(")").pieces("a)"))
    }

    @Test
    fun `without a closer to skip or without one in the text nothing is split`() {
        assertEquals(listOf(show("a): int")), CloserSplice("").pieces("a): int"))
        assertEquals(listOf(show("int \$a")), CloserSplice(")").pieces("int \$a"))
        assertEquals(emptyList(), CloserSplice(")").pieces(""))
    }

    @Test
    fun `a closer inside a string does not count`() {
        assertEquals(
            listOf(show("string \$s = ')'"), skip, show(": int")),
            CloserSplice(")").pieces("string \$s = ')'): int"),
        )
    }
}
