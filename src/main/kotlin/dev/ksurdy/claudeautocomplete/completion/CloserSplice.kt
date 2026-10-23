package dev.ksurdy.claudeautocomplete.completion

sealed interface SplicePiece {
    val text: String

    data class Show(override val text: String) : SplicePiece

    data class Skip(override val text: String) : SplicePiece
}

class CloserSplice(private val closer: String) {
    private var seen = ""
    private var done = closer.isEmpty()

    fun pieces(delta: String): List<SplicePiece> {
        if (delta.isEmpty()) return emptyList()
        val before = seen.length
        seen += delta
        if (done) return listOf(SplicePiece.Show(delta))
        val index = Brackets.firstUnmatchedIndex(seen, closer.first())
        if (index < 0) return listOf(SplicePiece.Show(delta))
        done = true
        val cut = (index - before).coerceAtLeast(0)
        return listOf(
            SplicePiece.Show(delta.take(cut)),
            SplicePiece.Skip(closer),
            SplicePiece.Show(delta.drop(cut + closer.length)),
        ).filter { it.text.isNotEmpty() }
    }
}
