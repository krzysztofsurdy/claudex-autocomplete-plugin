package dev.ksurdy.claudeautocomplete.context

import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnippetSelectorTest {
    private val query = "alpha beta gamma delta"
    private val budget = 10_000

    private fun php(path: String, content: String) = OpenFileSnippet(path, "PHP", content)

    private fun select(candidates: List<OpenFileSnippet>, language: String = "PHP", budget: Int = this.budget, query: String = this.query) =
        SnippetSelector().select(candidates, language, query, budget)

    @Test
    fun `best snippet comes last`() {
        val result = select(
            listOf(php("lo", "alpha beta"), php("hi", "alpha beta gamma delta"), php("mid", "alpha beta gamma")),
        )
        assertEquals(listOf("lo", "mid", "hi"), result.map { it.path })
    }

    @Test
    fun `keeps only the four best files`() {
        val files = (0..5).map { php("f$it", "alpha beta gamma delta " + (1..it).joinToString(" ") { n -> "extra$n" }) }
        assertEquals(listOf("f3", "f2", "f1", "f0"), select(files).map { it.path })
    }

    @Test
    fun `drops weak and single token matches`() {
        val weak = php("weak", "alpha beta " + (1..100).joinToString(" ") { "extra$it" })
        val single = php("single", "alpha")
        val strong = php("strong", "alpha beta gamma")
        assertEquals(listOf("strong"), select(listOf(weak, single, strong)).map { it.path })
    }

    @Test
    fun `reports one based line range of the best window`() {
        val lines = List(100) { "noise_a noise_b" }.toMutableList()
        lines[70] = "alpha"
        lines[71] = "beta"
        lines[72] = "gamma delta"
        val snippet = select(listOf(php("big.php", lines.joinToString("\n")))).single()
        assertTrue(snippet.startLine <= 71 && snippet.endLine >= 73, "${snippet.startLine}-${snippet.endLine}")
        assertEquals(snippet.endLine - snippet.startLine + 1, snippet.content.lines().size)
        assertEquals(lines.subList(snippet.startLine - 1, snippet.endLine).joinToString("\n"), snippet.content)
    }

    @Test
    fun `same language files win the slots over other languages`() {
        val same = (1..4).map { php("same$it", "alpha beta") }
        val other = OpenFileSnippet("other.kt", "kotlin", "alpha beta gamma delta")
        val result = select(same + other)
        assertEquals(4, result.size)
        assertTrue(result.none { it.path == "other.kt" })
    }

    @Test
    fun `other languages fill the remaining slots`() {
        val result = select(
            listOf(OpenFileSnippet("other.kt", "kotlin", "alpha beta gamma delta"), php("same", "alpha beta gamma")),
        )
        assertEquals(listOf("same", "other.kt"), result.map { it.path })
    }

    @Test
    fun `language match ignores case`() {
        val result = select(listOf(OpenFileSnippet("a", "php", "alpha beta")), language = "PHP")
        assertEquals(1, result.size)
    }

    @Test
    fun `window is cut to whole lines that fit the budget`() {
        val line = "alpha beta gamma delta"
        val file = php("f", List(50) { line }.joinToString("\n"))
        val snippet = select(listOf(file), budget = 100).single()
        assertEquals(1, snippet.startLine)
        assertEquals(4, snippet.endLine)
        assertTrue(snippet.content.length <= 100)
    }

    @Test
    fun `a cut shorter than three lines is dropped`() {
        val line = "alpha beta gamma delta"
        assertTrue(select(listOf(php("f", List(50) { line }.joinToString("\n"))), budget = 50).isEmpty())
    }

    @Test
    fun `total content stays within the budget across snippets`() {
        val line = "alpha beta gamma delta"
        val files = listOf(php("a", List(50) { line }.joinToString("\n")), php("b", List(50) { line }.joinToString("\n")))
        val result = select(files, budget = 100)
        assertTrue(result.sumOf { it.content.length } <= 100)
        assertEquals(1, result.size)
    }

    @Test
    fun `nothing without budget or meaningful query`() {
        val files = listOf(php("a", "alpha beta gamma delta"))
        assertTrue(select(files, budget = 0).isEmpty())
        assertTrue(select(files, query = "").isEmpty())
        assertTrue(select(files, query = "public function return").isEmpty())
        assertTrue(select(emptyList()).isEmpty())
    }

    @Test
    fun `scoring work is bounded for twenty large tabs`() {
        val files = (0 until 20).map { tab ->
            php("tab$tab.php", (0 until 3000).joinToString("\n") { "\$value = compute(alpha, beta, gamma, delta, tab$tab)" })
        }
        val selector = SnippetSelector()
        val result = selector.select(files, "PHP", query, budget)
        val stopWords = StopWords.forLanguage("PHP")
        val queryTokens = IdentifierTokenizer.tokens(query, stopWords)
        val tokens = files.sumOf { TokenizedFile.of(it.content, stopWords, queryTokens).tokenCount.toLong() }
        assertTrue(result.isNotEmpty())
        assertTrue(selector.operations <= 2 * tokens, "${selector.operations} operations for $tokens tokens")
    }

    @Test
    fun `scoring work ignores lines past the cap`() {
        val huge = php("huge.php", "alpha beta gamma\n".repeat(TokenizedFile.MAX_LINES * 10))
        val selector = SnippetSelector()
        selector.select(listOf(huge), "PHP", query, budget)
        assertTrue(selector.operations <= 2L * 3 * TokenizedFile.MAX_LINES)
    }
}
