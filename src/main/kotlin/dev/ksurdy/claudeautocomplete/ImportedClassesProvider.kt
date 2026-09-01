package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet

interface ImportedClassesProvider {
    fun collect(file: PsiFile): List<OpenFileSnippet>

    companion object {
        val EP_NAME: ExtensionPointName<ImportedClassesProvider> =
            ExtensionPointName.create("dev.ksurdy.claudeautocomplete.importedClassesProvider")
    }
}
