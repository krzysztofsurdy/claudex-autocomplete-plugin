package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.BackendConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.ProviderKind

class CompletionCache(
    private val capacity: Int,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val value: String, val storedAt: Long)

    private val entries = object : LinkedHashMap<CacheKey, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Entry>): Boolean = size > capacity
    }

    @Synchronized
    fun get(key: CacheKey): String? {
        val exact = entries[key]
        if (exact != null && !isExpired(exact)) return exact.value
        return forward(key)
    }

    @Synchronized
    fun put(key: CacheKey, value: String) {
        entries[key] = Entry(value, clock())
    }

    private fun forward(key: CacheKey): String? {
        var hitKey: CacheKey? = null
        var remainder: String? = null
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (storedKey, entry) = iterator.next()
            if (isExpired(entry)) {
                iterator.remove()
                continue
            }
            val rest = key.remainderOf(storedKey, entry.value) ?: continue
            hitKey = storedKey
            remainder = rest
        }
        hitKey?.let { entries[it] }
        return remainder
    }

    private fun isExpired(entry: Entry): Boolean = clock() - entry.storedAt > ttlMs

    companion object {
        private const val DEFAULT_TTL_MS = 60_000L

        fun key(context: CompletionContext, config: BackendConfig): CacheKey =
            CacheKey.of(scope(context, config), context.prefix, context.suffix, context.multiline)

        fun scope(context: CompletionContext, config: BackendConfig): String = (listOf(
            config.provider.name,
            *providerSettings(config).toTypedArray(),
            context.filePath,
            context.languageId,
            context.maxLines.toString(),
        ) + context.importedClasses.map { it.path + "\u0001" + it.content }).joinToString("\u0000")

        private fun providerSettings(config: BackendConfig): List<String> = when (config.provider) {
            ProviderKind.Claude -> listOf(config.claude.model, config.claude.effort, config.claude.customInstructions)
            ProviderKind.Codex -> listOf(config.codex.model, config.codex.reasoningEffort, config.codex.customInstructions)
        }
    }
}
