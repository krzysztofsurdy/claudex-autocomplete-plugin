package dev.ksurdy.claudeautocomplete.completion

data class InFlightOrigin(val scope: String, val prefix: String, val suffix: String, val multiline: Boolean)

sealed interface InFlightVerdict {
    data class Reuse(val typed: String) : InFlightVerdict

    data object Diverged : InFlightVerdict
}

object InFlightMatch {
    fun decide(origin: InFlightOrigin, wanted: InFlightOrigin, streamed: String): InFlightVerdict {
        if (origin.scope != wanted.scope || origin.suffix != wanted.suffix) return InFlightVerdict.Diverged
        if (wanted.multiline && !origin.multiline) return InFlightVerdict.Diverged
        if (!wanted.prefix.startsWith(origin.prefix)) return InFlightVerdict.Diverged
        val typed = wanted.prefix.substring(origin.prefix.length)
        val consistent = streamed.startsWith(typed) || typed.startsWith(streamed)
        return if (consistent) InFlightVerdict.Reuse(typed) else InFlightVerdict.Diverged
    }
}
