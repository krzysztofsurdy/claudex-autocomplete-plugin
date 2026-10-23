package dev.ksurdy.claudeautocomplete.alternatives

import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.util.UserDataHolderBase
import dev.ksurdy.claudeautocomplete.elementsOf
import kotlinx.coroutines.flow.asFlow

class AlternativeSuggestion(private val texts: List<String>, private val skippedCloser: String = "") : InlineCompletionSuggestion {
    override suspend fun getVariants(): List<InlineCompletionVariant> =
        texts.map { InlineCompletionVariant.build(UserDataHolderBase(), elementsOf(it, skippedCloser).asFlow()) }
}
