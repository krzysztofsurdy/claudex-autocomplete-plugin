package dev.ksurdy.claudeautocomplete.context

object CaretQuery {
    const val LINES = 60

    fun around(prefix: String, suffix: String, lines: Int = LINES): String {
        var start = prefix.lastIndexOf('\n') + 1
        var remaining = lines - 1
        while (remaining > 0 && start > 0) {
            start = prefix.lastIndexOf('\n', start - 2) + 1
            remaining--
        }
        val newline = suffix.indexOf('\n')
        return prefix.substring(start) + if (newline < 0) suffix else suffix.substring(0, newline)
    }
}
