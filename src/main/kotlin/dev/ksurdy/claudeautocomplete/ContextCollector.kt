package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.psi.PsiManager
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
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
        val window = FileWindow.compute(
            text,
            offset,
            settings.contextMode.orEmpty(),
            settings.wholeFileMaxLines,
            settings.linesAroundCursor,
        )
        val prefix = window.prefix
        val suffix = window.suffix
        if (!force && !TriggerRules.shouldTrigger(prefix, suffix)) return null

        val project = editor.project
        val currentFile = request.file.virtualFile
        val patterns = settings.excludedFilePatterns.orEmpty()
        if (project != null && currentFile != null && isExcluded(project, currentFile, patterns)) return null
        return CompletionContext(
            filePath = currentFile?.let { relativePath(project, it) } ?: request.file.name,
            languageId = languageId,
            prefix = prefix,
            suffix = suffix,
            openFiles = if (settings.includeOpenTabs && project != null) {
                openTabs(project, currentFile, settings.maxOpenTabsChars, patterns)
            } else {
                emptyList()
            },
            multiline = TriggerRules.isMultiline(prefix, suffix, settings.multilineMode.orEmpty()),
            maxLines = settings.maxCompletionLines,
            indent = indentOf(request),
            prefixTruncated = window.prefixTruncated,
            suffixTruncated = window.suffixTruncated,
            importedClasses = importedClasses(request, settings),
        )
    }

    private fun importedClasses(request: InlineCompletionRequest, settings: ClaudeAutocompleteSettings.State): List<OpenFileSnippet> {
        val project = request.editor.project ?: return emptyList()
        if (!settings.includeImportedClasses || DumbService.isDumb(project)) return emptyList()
        val snippets = ImportedClassesProvider.EP_NAME.extensionList.flatMap { it.collect(request.file) }
        return ContextLimits.budget(snippets, settings.maxImportedClassesChars)
    }

    private fun indentOf(request: InlineCompletionRequest): String {
        val options = CodeStyle.getIndentOptions(request.file)
        return if (options.USE_TAB_CHARACTER) "tabs" else "${options.INDENT_SIZE} spaces"
    }

    private fun openTabs(project: Project, current: VirtualFile?, budget: Int, patterns: String): List<OpenFileSnippet> {
        val manager = FileEditorManager.getInstance(project)
        val documents = FileDocumentManager.getInstance()
        val open = manager.openFiles.toSet()
        val recent = EditorHistoryManager.getInstance(project).fileList.asReversed().filter { it in open }
        val ordered = (manager.selectedFiles.toList() + recent + open).distinct()
        val psiManager = PsiManager.getInstance(project)
        val snippets = ordered
            .filter { it != current && it.isValid && !it.fileType.isBinary && !isExcluded(project, it, patterns) }
            .mapNotNull { file ->
                val content = documents.getCachedDocument(file)?.charsSequence ?: return@mapNotNull null
                if (content.length > MAX_FILE_CHARS) return@mapNotNull null
                OpenFileSnippet(relativePath(project, file), psiManager.findFile(file)?.language?.id ?: file.fileType.name, content.toString())
            }
        return ContextLimits.budget(snippets, budget)
    }

    fun isExcluded(project: Project, file: VirtualFile, patterns: String): Boolean {
        if (SensitiveFiles.isSensitive(file.name, patterns)) return true
        if (ProjectFileIndex.getInstance(project).isExcluded(file)) return true
        return ProjectLevelVcsManager.getInstance(project).hasActiveVcss() &&
            ChangeListManager.getInstance(project).isIgnoredFile(file)
    }

    private fun relativePath(project: Project?, file: VirtualFile): String {
        val base = project?.guessProjectDir() ?: return file.name
        return VfsUtilCore.getRelativePath(file, base) ?: file.path
    }
}
