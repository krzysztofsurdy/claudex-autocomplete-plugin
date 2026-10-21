package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ContextCollector
import dev.ksurdy.claudeautocomplete.ContextLimits
import dev.ksurdy.claudeautocomplete.backend.CompletionContext

data class NextEditRequest(val context: CompletionContext, val modificationStamp: Long, val caretOffset: Int)

object NextEditRequestFactory {
    fun create(editor: Editor, history: EditHistory, settings: ClaudeAutocompleteSettings.State): NextEditRequest? {
        val project = editor.project ?: return null
        val document = editor.document
        if (editor.isDisposed || editor.isViewer || !document.isWritable) return null
        if (document.textLength > ContextCollector.MAX_FILE_CHARS) return null
        val file = FileDocumentManager.getInstance().getFile(document) ?: return null
        if (ContextCollector.isExcluded(project, file, settings.excludedFilePatterns.orEmpty())) return null
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        if (ContextLimits.isLanguageDisabled(psiFile.language.id, settings.disabledLanguages.orEmpty())) return null
        val hunks = history.hunks()
        if (hunks.isEmpty()) return null
        val caretOffset = editor.caretModel.offset
        val input = NextEditInput.create(
            hunks,
            document.charsSequence,
            caretOffset,
            settings.contextMode.orEmpty(),
            settings.wholeFileMaxLines,
            settings.linesAroundCursor,
        )
        val options = CodeStyle.getIndentOptions(psiFile)
        val context = CompletionContext(
            filePath = ContextCollector.relativePath(project, file),
            languageId = psiFile.language.id,
            prefix = "",
            suffix = "",
            relatedSnippets = emptyList(),
            multiline = false,
            maxLines = NextEditTarget.MAX_LINES,
            indent = if (options.USE_TAB_CHARACTER) "tabs" else "${options.INDENT_SIZE} spaces",
            nextEdit = input,
        )
        return NextEditRequest(context, document.modificationStamp, caretOffset)
    }
}
