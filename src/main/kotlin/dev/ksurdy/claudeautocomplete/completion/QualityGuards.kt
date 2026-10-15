package dev.ksurdy.claudeautocomplete.completion

import dev.ksurdy.claudeautocomplete.backend.CompletionContext

enum class Guard(val label: String) {
    Repetition("repetition guard"),
}

class GuardCut(val guard: Guard, val linesBefore: Int, val linesAfter: Int)

object QualityGuards {
    class Result(val text: String, val cuts: List<GuardCut>)

    fun apply(text: String, context: CompletionContext): Result {
        val cuts = mutableListOf<GuardCut>()
        var current = text
        for ((guard, run) in stages(context)) {
            val next = run(current) ?: continue
            cuts += GuardCut(guard, lineCount(current), lineCount(next))
            current = next
        }
        return Result(current, cuts)
    }

    fun holdBack(text: String, context: CompletionContext): String = RepetitionGuard.holdBack(text)

    private fun stages(context: CompletionContext): List<Pair<Guard, (String) -> String?>> = listOf(
        Guard.Repetition to { text -> RepetitionGuard.cut(text) },
    )

    private fun lineCount(text: String): Int = if (text.isBlank()) 0 else text.lines().size
}
