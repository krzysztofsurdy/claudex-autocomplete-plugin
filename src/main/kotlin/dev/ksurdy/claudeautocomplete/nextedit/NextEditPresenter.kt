package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import dev.ksurdy.claudeautocomplete.ClaudexBundle
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle

class NextEditPresenter(private val editor: Editor) {
    private var highlighter: RangeHighlighter? = null
    private var inlay: Inlay<PreviewRenderer>? = null

    val isShowing: Boolean get() = highlighter != null || inlay != null

    val previewLines: List<String> get() = inlay?.renderer?.lines.orEmpty()

    val hint: String? get() = inlay?.renderer?.hint

    fun show(edit: NextEdit) {
        clear()
        val scheme = editor.colorsScheme
        val removed = TextAttributes().apply {
            backgroundColor = scheme.getAttributes(DiffColors.DIFF_MODIFIED).backgroundColor
            effectType = EffectType.STRIKEOUT
            effectColor = scheme.defaultForeground
        }
        highlighter = editor.markupModel.addRangeHighlighter(
            edit.startOffset,
            edit.endOffset,
            HighlighterLayer.SELECTION - 1,
            removed,
            HighlighterTargetArea.EXACT_RANGE,
        )
        val anchor = editor.document.getLineEndOffset(edit.endLine - 1)
        val properties = InlayProperties().relatesToPrecedingText(true).showAbove(false)
        inlay = editor.inlayModel.addBlockElement(anchor, properties, PreviewRenderer(editor, edit.newText, jumpHint()))
    }

    fun showApplyHint() {
        val current = inlay ?: return
        current.renderer.hint = applyHint()
        current.update()
    }

    fun clear() {
        highlighter?.let { editor.markupModel.removeHighlighter(it) }
        highlighter = null
        inlay?.dispose()
        inlay = null
    }

    private fun jumpHint(): String = ClaudexBundle.message("nextedit.hint.jump")

    private fun applyHint(): String = ClaudexBundle.message("nextedit.hint.apply")

    class PreviewRenderer(private val editor: Editor, newText: String, var hint: String) : EditorCustomElementRenderer {
        val lines: List<String> = if (newText.isEmpty()) emptyList() else newText.split('\n')

        private val textFont: Font get() = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        private val hintFont: Font get() = InlineCompletionFontUtils.font(editor).deriveFont(Font.ITALIC)

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val text = editor.contentComponent.getFontMetrics(textFont)
            val hintMetrics = editor.contentComponent.getFontMetrics(hintFont)
            val widest = lines.maxOfOrNull { text.stringWidth(expandTabs(it)) } ?: 0
            return maxOf(widest, hintMetrics.stringWidth(hint)) + PADDING * 2
        }

        override fun calcHeightInPixels(inlay: Inlay<*>): Int = (lines.size + 1) * editor.lineHeight

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val scheme = editor.colorsScheme
            scheme.getAttributes(DiffColors.DIFF_INSERTED).backgroundColor?.let {
                g.color = it
                g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
            }
            val ascent = editor.ascent()
            g.font = textFont
            g.color = scheme.defaultForeground
            lines.forEachIndexed { index, line ->
                g.drawString(expandTabs(line), targetRegion.x + PADDING, targetRegion.y + index * editor.lineHeight + ascent)
            }
            g.font = hintFont
            g.color = InlineCompletionFontUtils.color(editor)
            g.drawString(hint, targetRegion.x + PADDING, targetRegion.y + lines.size * editor.lineHeight + ascent)
        }

        private fun expandTabs(line: String): String = line.replace("\t", "    ")

        private fun Editor.ascent(): Int = (this as? EditorImpl)?.ascent ?: lineHeight
    }

    private companion object {
        const val PADDING = 4
    }
}
