package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.ClaudeConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext

class CompletionCache(
    private val capacity: Int,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val value: String, val storedAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > capacity
    }

    @Synchronized
    fun get(key: String): String? {
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: String) {
        entries[key] = Entry(value, clock())
    }

    companion object {
        private const val PREFIX_TAIL_CHARS = 500
        private const val SUFFIX_HEAD_CHARS = 200
        private const val DEFAULT_TTL_MS = 60_000L

        fun key(context: CompletionContext, config: ClaudeConfig): String = (listOf(
            config.model,
            config.effort,
            config.customInstructions,
            context.filePath,
            context.languageId,
            context.maxLines.toString(),
            context.multiline.toString(),
            context.prefix.takeLast(PREFIX_TAIL_CHARS),
            context.suffix.take(SUFFIX_HEAD_CHARS),
        ) + context.importedClasses.map { it.path + "\u0001" + it.content }).joinToString("\u0000")
    }
}
