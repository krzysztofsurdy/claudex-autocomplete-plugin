package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.psi.PsiManager
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet
import dev.ksurdy.claudeautocomplete.completion.TriggerRules

object ContextCollector {
    const val MAX_FILE_CHARS = 1_000_000

    fun collect(
        request: InlineCompletionRequest,
        settings: ClaudeAutocompleteSettings.State,
        force: Boolean = false,
    ): CompletionContext? {
        val editor = request.editor
        if (editor.isViewer || !request.document.isWritable) return null
        val text = request.document.charsSequence
        if (text.length > MAX_FILE_CHARS) return null
        val languageId = request.file.language.id
        if (ContextLimits.isLanguageDisabled(languageId, settings.disabledLanguages.orEmpty())) return null

        val offset = request.endOffset.coerceIn(0, text.length)
        val prefixStart = maxOf(0, offset - settings.maxPrefixChars)
        val suffixEnd = minOf(text.length, offset + settings.maxSuffixChars)
        val prefix = text.subSequence(prefixStart, offset).toString()
        val suffix = text.subSequence(offset, suffixEnd).toString()
        if (!force && !TriggerRules.shouldTrigger(prefix, suffix)) return null

        val project = editor.project
        val currentFile = request.file.virtualFile
        return CompletionContext(
            filePath = currentFile?.let { relativePath(project, it) } ?: request.file.name,
            languageId = languageId,
            prefix = prefix,
            suffix = suffix,
            openFiles = if (settings.includeOpenTabs && project != null) {
                openTabs(project, currentFile, settings.maxOpenTabsChars)
            } else {
                emptyList()
            },
            multiline = TriggerRules.isMultiline(prefix, suffix, settings.multilineMode.orEmpty()),
            maxLines = settings.maxCompletionLines,
            indent = indentOf(request),
            prefixTruncated = prefixStart > 0,
            suffixTruncated = suffixEnd < text.length,
        )
    }

    private fun indentOf(request: InlineCompletionRequest): String {
        val options = CodeStyle.getIndentOptions(request.file)
        return if (options.USE_TAB_CHARACTER) "tabs" else "${options.INDENT_SIZE} spaces"
    }

    private fun openTabs(project: Project, current: VirtualFile?, budget: Int): List<OpenFileSnippet> {
        val manager = FileEditorManager.getInstance(project)
        val documents = FileDocumentManager.getInstance()
        val open = manager.openFiles.toSet()
        val recent = EditorHistoryManager.getInstance(project).fileList.asReversed().filter { it in open }
        val ordered = (manager.selectedFiles.toList() + recent + open).distinct()
        val psiManager = PsiManager.getInstance(project)
        val snippets = ordered
            .filter { it != current && it.isValid && !it.fileType.isBinary }
            .mapNotNull { file ->
                val content = documents.getCachedDocument(file)?.charsSequence ?: return@mapNotNull null
                if (content.length > MAX_FILE_CHARS) return@mapNotNull null
                OpenFileSnippet(relativePath(project, file), psiManager.findFile(file)?.language?.id ?: file.fileType.name, content.toString())
            }
        return ContextLimits.budget(snippets, budget)
    }

    private fun relativePath(project: Project?, file: VirtualFile): String {
        val base = project?.guessProjectDir() ?: return file.name
        return VfsUtilCore.getRelativePath(file, base) ?: file.path
    }
}
