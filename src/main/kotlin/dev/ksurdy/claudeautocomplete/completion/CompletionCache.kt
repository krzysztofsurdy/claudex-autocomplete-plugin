package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

class CompletionCache(private val capacity: Int) {
    private val entries = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > capacity
    }

    @Synchronized
    fun get(key: String): String? = entries[key]

    @Synchronized
    fun put(key: String, value: String) {
        entries[key] = value
    }

    companion object {
        private const val PREFIX_TAIL_CHARS = 500
        private const val SUFFIX_HEAD_CHARS = 200

        fun key(context: CompletionContext, model: String): String {
            val raw = listOf(
                model,
                context.multiline.toString(),
                context.prefix.takeLast(PREFIX_TAIL_CHARS),
                context.suffix.take(SUFFIX_HEAD_CHARS),
            ).joinToString("\u0000")
            return raw.hashCode().toString() + ":" + raw.length
        }
    }
}
