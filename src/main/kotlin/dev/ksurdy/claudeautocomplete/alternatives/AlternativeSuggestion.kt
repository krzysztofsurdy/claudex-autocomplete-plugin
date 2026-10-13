package dev.ksurdy.claudeautocomplete.alternatives

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.flow.flowOf

class AlternativeSuggestion(private val texts: List<String>) : InlineCompletionSuggestion {
    override suspend fun getVariants(): List<InlineCompletionVariant> =
        texts.map { InlineCompletionVariant.build(UserDataHolderBase(), flowOf(InlineCompletionGrayTextElement(it))) }
}
