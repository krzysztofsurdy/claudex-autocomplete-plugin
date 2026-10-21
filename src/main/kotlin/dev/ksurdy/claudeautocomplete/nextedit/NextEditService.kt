package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
class NextEditService(val scope: CoroutineScope) {
    companion object {
        fun getInstance(): NextEditService = service()
    }
}
