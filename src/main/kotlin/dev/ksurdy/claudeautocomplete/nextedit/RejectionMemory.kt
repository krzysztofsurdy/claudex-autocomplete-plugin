package dev.ksurdy.claudeautocomplete.nextedit

class RejectionMemory(private val clock: () -> Long, private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private data class Key(val file: String, val startLine: Int, val endLine: Int, val text: String)

    private val entries = LinkedHashMap<Key, Long>()

    @Synchronized
    fun remember(file: String, startLine: Int, endLine: Int, replacement: String) {
        prune()
        val key = Key(file, startLine, endLine, normalise(replacement))
        entries.remove(key)
        entries[key] = clock()
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun contains(file: String, startLine: Int, endLine: Int, replacement: String): Boolean {
        prune()
        return Key(file, startLine, endLine, normalise(replacement)) in entries
    }

    private fun prune() {
        val now = clock()
        entries.values.removeAll { now - it >= TTL_MS }
    }

    private fun normalise(text: String): String = text.trim().replace(WHITESPACE, " ")

    companion object {
        const val TTL_MS = 5 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 50
        private val WHITESPACE = Regex("\\s+")
    }
}
