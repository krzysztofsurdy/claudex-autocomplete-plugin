package dev.ksurdy.claudeautocomplete.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowScorerTest {
    private val none = emptySet<String>()

    private fun file(text: String, query: Set<String>) = TokenizedFile.of(text, none, query)

    private fun noise(lines: Int) = List(lines) { "noise_a noise_b" }

    @Test
    fun `score is intersection over union of distinct tokens`() {
        val query = setOf("alpha", "beta", "delta")
        val best = assertNotNull(WindowScorer().best(file("alpha beta\ngamma", query), query.size))
        assertEquals(0.5, best.score, 1e-9)
        assertEquals(2, best.shared)
        assertEquals(0, best.startLine)
        assertEquals(2, best.endLine)
    }

    @Test
    fun `no shared token gives no window`() {
        val query = setOf("alpha")
        assertNull(WindowScorer().best(file("beta\ngamma", query), query.size))
        assertNull(WindowScorer().best(file("", query), query.size))
    }

    @Test
    fun `finds the window around the matching lines`() {
        val query = setOf("alpha", "beta", "gamma", "delta", "epsilon")
        val lines = noise(200).toMutableList()
        query.sorted().forEachIndexed { index, token -> lines[120 + index] = token }
        val best = assertNotNull(WindowScorer().best(file(lines.joinToString("\n"), query), query.size))
        assertTrue(best.startLine <= 120 && best.endLine >= 125, "${best.startLine}-${best.endLine}")
        assertEquals(60, best.endLine - best.startLine)
    }

    @Test
    fun `the last lines of a file are covered`() {
        val query = setOf("alpha", "beta")
        val lines = noise(133).toMutableList()
        lines[132] = "alpha beta"
        val best = assertNotNull(WindowScorer().best(file(lines.joinToString("\n"), query), query.size))
        assertEquals(133, best.endLine)
        assertEquals(73, best.startLine)
    }

    @Test
    fun `a short file is one window`() {
        val query = setOf("alpha")
        val best = assertNotNull(WindowScorer().best(file("x1\nalpha\nx2", query), query.size))
        assertEquals(0, best.startLine)
        assertEquals(3, best.endLine)
    }

    @Test
    fun `the earliest of equally good windows wins`() {
        val query = setOf("alpha", "beta")
        val lines = noise(200).toMutableList()
        lines[10] = "alpha beta"
        lines[150] = "alpha beta"
        val best = assertNotNull(WindowScorer().best(file(lines.joinToString("\n"), query), query.size))
        assertTrue(best.startLine <= 10)
    }

    @Test
    fun `work is bounded by two operations per token`() {
        val query = setOf("alpha", "beta")
        val text = (0 until 3000).joinToString("\n") { "val item$it = compute(alpha, beta, \$total)" }
        val tokenized = file(text, query)
        val scorer = WindowScorer()
        scorer.best(tokenized, query.size)
        assertTrue(scorer.operations > 0)
        assertTrue(scorer.operations <= 2L * tokenized.tokenCount, "${scorer.operations} for ${tokenized.tokenCount} tokens")
    }

    @Test
    fun `work does not grow past the line cap`() {
        val query = setOf("alpha", "beta")
        val text = "alpha beta gamma\n".repeat(TokenizedFile.MAX_LINES * 10)
        val scorer = WindowScorer()
        scorer.best(file(text, query), query.size)
        assertTrue(scorer.operations <= 2L * 3 * TokenizedFile.MAX_LINES)
    }
}
