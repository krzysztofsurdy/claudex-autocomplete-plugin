package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import dev.ksurdy.claudeautocomplete.completion.CloserSplice
import dev.ksurdy.claudeautocomplete.completion.SplicePiece

fun CloserSplice.elements(delta: String): List<InlineCompletionElement> = pieces(delta).map { piece ->
    when (piece) {
        is SplicePiece.Show -> InlineCompletionGrayTextElement(piece.text)
        is SplicePiece.Skip -> InlineCompletionSkipTextElement(piece.text)
    }
}

fun elementsOf(text: String, skippedCloser: String): List<InlineCompletionElement> = CloserSplice(skippedCloser).elements(text)
