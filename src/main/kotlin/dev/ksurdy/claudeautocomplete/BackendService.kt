package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.ksurdy.claudeautocomplete.backend.ClaudeCliBackend
import dev.ksurdy.claudeautocomplete.backend.CompletionBackend

@Service(Service.Level.APP)
class BackendService : Disposable {
    @Volatile
    var backend: CompletionBackend = ClaudeCliBackend()

    override fun dispose() {
        backend.shutdown()
    }

    companion object {
        fun getInstance(): BackendService = service()
    }
}
