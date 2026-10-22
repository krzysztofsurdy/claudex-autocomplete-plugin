package dev.ksurdy.claudeautocomplete.completion

class TypedTrim(private val typed: String) {
    var diverged: Boolean = false
        private set

    var hasRest: Boolean = false
        private set

    private var emitted = 0

    fun advance(text: String): String {
        if (diverged) return ""
        val common = minOf(text.length, typed.length)
        if (text.regionMatches(0, typed, 0, common).not()) {
            diverged = true
            return ""
        }
        if (text.length <= typed.length) return ""
        hasRest = true
        val rest = text.substring(typed.length)
        if (rest.length <= emitted) return ""
        val delta = rest.substring(emitted)
        emitted = rest.length
        return delta
    }
}
