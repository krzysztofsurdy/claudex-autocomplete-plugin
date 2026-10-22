package dev.ksurdy.claudeautocomplete.context

import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet
import dev.ksurdy.claudeautocomplete.backend.RelatedSnippet

class SnippetSelector(private val scorer: WindowScorer = WindowScorer()) {
    val operations: Long get() = scorer.operations

    private class Candidate(val file: TokenizedFile, val path: String, val window: ScoredWindow)

    private class Pick(val snippet: RelatedSnippet, val score: Double)

    fun select(candidates: List<OpenFileSnippet>, languageId: String, query: String, charBudget: Int): List<RelatedSnippet> {
        if (charBudget <= 0) return emptyList()
        val queryTokens = IdentifierTokenizer.tokens(query, StopWords.forLanguage(languageId))
        if (queryTokens.isEmpty()) return emptyList()
        val (sameLanguage, otherLanguages) = candidates.partition { it.languageId.equals(languageId, ignoreCase = true) }
        val picks = ArrayList<Pick>()
        var remaining = charBudget
        for (group in listOf(sameLanguage, otherLanguages)) {
            if (picks.size >= MAX_SNIPPETS || remaining <= 0) break
            for (candidate in rank(group, queryTokens)) {
                if (picks.size >= MAX_SNIPPETS) break
                val pick = fit(candidate, remaining) ?: continue
                picks += pick
                remaining -= pick.snippet.content.length
            }
        }
        return picks.sortedBy { it.score }.map { it.snippet }
    }

    private fun rank(files: List<OpenFileSnippet>, queryTokens: Set<String>): List<Candidate> =
        files.mapNotNull { file ->
            val tokenized = TokenizedFile.of(file.content, StopWords.forLanguage(file.languageId), queryTokens)
            val window = scorer.best(tokenized, queryTokens.size)
            if (window != null && window.score >= MIN_SCORE && window.shared >= MIN_SHARED_TOKENS) {
                Candidate(tokenized, file.path, window)
            } else {
                null
            }
        }.sortedByDescending { it.window.score }

    private fun fit(candidate: Candidate, remaining: Int): Pick? {
        val window = candidate.window
        var end = window.endLine
        while (end > window.startLine && candidate.file.length(window.startLine, end) > remaining) end--
        val cut = end < window.endLine
        if (end == window.startLine || (cut && end - window.startLine < MIN_CUT_LINES)) return null
        val snippet = RelatedSnippet(candidate.path, window.startLine + 1, end, candidate.file.slice(window.startLine, end))
        return Pick(snippet, window.score)
    }

    companion object {
        const val MAX_SNIPPETS = 4
        const val MIN_SCORE = 0.1
        const val MIN_SHARED_TOKENS = 2
        const val MIN_CUT_LINES = 3
    }
}
