package dev.ksurdy.claudeautocomplete.alternatives

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

class AlternativeRequest(val previousTexts: List<String>) {
    companion object {
        const val MAX_VARIANTS = 3
        private val KEY = Key.create<AlternativeRequest>("dev.ksurdy.claudeautocomplete.alternativeRequest")

        fun put(editor: Editor, request: AlternativeRequest) = editor.putUserData(KEY, request)

        fun isPending(editor: Editor): Boolean = editor.getUserData(KEY) != null

        fun take(editor: Editor): AlternativeRequest? {
            val request = editor.getUserData(KEY) ?: return null
            editor.putUserData(KEY, null)
            return request
        }
    }
}
