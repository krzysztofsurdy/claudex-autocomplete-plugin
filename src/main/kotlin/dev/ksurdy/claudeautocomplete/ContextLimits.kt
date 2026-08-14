package dev.ksurdy.claudeautocomplete

import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet

object ContextLimits {
    fun tail(text: String, max: Int): String = if (text.length <= max) text else text.takeLast(max.coerceAtLeast(0))

    fun head(text: String, max: Int): String = if (text.length <= max) text else text.take(max.coerceAtLeast(0))

    fun budget(snippets: List<OpenFileSnippet>, totalChars: Int): List<OpenFileSnippet> {
        var remaining = totalChars
        val result = ArrayList<OpenFileSnippet>()
        snippets.forEachIndexed { index, snippet ->
            val share = remaining / (snippets.size - index)
            val content = head(snippet.content, share)
            remaining -= content.length
            if (content.isNotEmpty()) result += snippet.copy(content = content)
        }
        return result
    }

    fun isLanguageDisabled(languageId: String, disabledLanguages: String): Boolean =
        disabledLanguages.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .any { it.equals(languageId, ignoreCase = true) }
}
