package dev.ksurdy.claudeautocomplete.alternatives

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.editor.Editor
import dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider

internal fun claudeSession(editor: Editor): InlineCompletionSession? =
    InlineCompletionSession.getOrNull(editor)?.takeIf { it.isActive() && it.provider is ClaudeInlineCompletionProvider }
