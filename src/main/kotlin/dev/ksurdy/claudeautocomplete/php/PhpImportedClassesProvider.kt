package dev.ksurdy.claudeautocomplete.php

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpUseList
import dev.ksurdy.claudeautocomplete.ImportedClassesProvider
import dev.ksurdy.claudeautocomplete.backend.OpenFileSnippet

class PhpImportedClassesProvider : ImportedClassesProvider {
    override fun collect(file: PsiFile): List<OpenFileSnippet> {
        if (file !is PhpFile) return emptyList()
        val index = PhpIndex.getInstance(file.project)
        val declared = PsiTreeUtil.findChildrenOfType(file, PhpClass::class.java)
        val declaredFqns = declared.map { it.fqn }.toSet()
        val related = declared.flatMap { relatedFqns(it) }
        val fqns = (importedFqns(file) + related).distinct().filter { it !in declaredFqns }
        return fqns.mapNotNull { fqn ->
            index.getAnyByFQN(fqn).firstOrNull()?.let { OpenFileSnippet(it.fqn.trimStart('\\'), "PHP", PhpClassOutline.render(it)) }
        }
    }

    private fun importedFqns(file: PhpFile): List<String> =
        PsiTreeUtil.findChildrenOfType(file, PhpUseList::class.java)
            .filter { !it.isOfConst && !it.isOfFunction && !it.isTraitImport }
            .flatMap { list -> list.declarations.filter { !it.isOfConst && !it.isOfFunction && !it.isTraitImport }.map { it.fqn } }

    private fun relatedFqns(phpClass: PhpClass): List<String> =
        listOfNotNull(phpClass.superClass?.fqn) +
            phpClass.directImplementedInterfaces.map { it.fqn } +
            phpClass.traits.map { it.fqn }
}
