package dev.ksurdy.claudeautocomplete.completion

data class CacheKey(
    val scope: String,
    val prefixTail: String,
    val suffixHead: String,
    val multiline: Boolean,
) {
    fun remainderOf(stored: CacheKey, value: String): String? {
        if (scope != stored.scope || suffixHead != stored.suffixHead) return null
        val longest = minOf(value.length - 1, prefixTail.length, MAX_TYPED_CHARS)
        for (typedLength in 0..longest) {
            if (!extendsBy(stored.prefixTail, value, typedLength)) continue
            val remainder = value.substring(typedLength)
            if (multiline || !remainder.contains('\n')) return remainder
        }
        return null
    }

    private fun extendsBy(storedTail: String, value: String, typedLength: Int): Boolean {
        if (!prefixTail.endsWith(value.substring(0, typedLength))) return false
        val anchor = prefixTail.dropLast(typedLength)
        return if (prefixTail.length < PREFIX_TAIL_CHARS) storedTail == anchor else storedTail.endsWith(anchor)
    }

    companion object {
        const val PREFIX_TAIL_CHARS = 500
        const val SUFFIX_HEAD_CHARS = 200
        private const val MAX_TYPED_CHARS = 200

        fun of(scope: String, prefix: String, suffix: String, multiline: Boolean): CacheKey =
            CacheKey(scope, prefix.takeLast(PREFIX_TAIL_CHARS), suffix.take(SUFFIX_HEAD_CHARS), multiline)
    }
}
