package dev.ksurdy.claudeautocomplete.alternatives

object AlternativeTexts {
    private val WHITESPACE = Regex("\\s+")

    fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim().trimEnd(';', ',', '.').trimEnd()

    fun isDuplicate(text: String, shown: List<String>): Boolean {
        val key = normalize(text)
        return key.isNotEmpty() && shown.any { normalize(it) == key }
    }

    suspend fun fetchDistinct(shown: List<String>, request: suspend (retry: Boolean) -> String): String? {
        val first = request(false)
        if (first.isBlank()) return null
        if (!isDuplicate(first, shown)) return first
        return request(true).takeIf { it.isNotBlank() && !isDuplicate(it, shown) }
    }

    fun variants(fresh: String?, shown: List<String>): List<String> = listOfNotNull(fresh) + shown
}
