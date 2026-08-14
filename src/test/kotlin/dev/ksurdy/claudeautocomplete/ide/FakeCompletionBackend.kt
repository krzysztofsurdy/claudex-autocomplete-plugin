package dev.ksurdy.claudeautocomplete.ide

import dev.ksurdy.claudeautocomplete.backend.ClaudeConfig
import dev.ksurdy.claudeautocomplete.backend.CompletionBackend
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import java.util.concurrent.CopyOnWriteArrayList

class FakeCompletionBackend(var result: CompletionResult) : CompletionBackend {
    val contexts = CopyOnWriteArrayList<CompletionContext>()

    override suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult {
        contexts += context
        return result
    }

    override fun shutdown() = Unit
}
