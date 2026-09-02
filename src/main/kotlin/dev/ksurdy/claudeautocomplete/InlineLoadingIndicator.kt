package dev.ksurdy.claudeautocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Timer

class InlineLoadingIndicator private constructor(private val editor: Editor, private val offset: Int) : Disposable {
    private val stopped = AtomicBoolean(false)
    private var inlay: Inlay<*>? = null
    private var tick = 0
    private var elapsedVisible = false
    private val startedAtNanos = System.nanoTime()
    private val timer = Timer(LoadingFrames.SHOW_DELAY_MS) { onTick() }.apply { isRepeats = false }
    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) = stop()
    }

    private fun begin() {
        Disposer.register(editorDisposable(), this)
        editor.caretModel.addCaretListener(caretListener, this)
        timer.start()
    }

    private fun editorDisposable(): Disposable = (editor as? EditorImpl)?.disposable ?: ApplicationManager.getApplication()

    private fun onTick() {
        if (stopped.get() || editor.isDisposed) return
        val current = inlay
        if (current == null) {
            inlay = editor.inlayModel.addAfterLineEndElement(offset.coerceIn(0, editor.document.textLength), false, Renderer())
            timer.initialDelay = LoadingFrames.FRAME_INTERVAL_MS
            timer.delay = LoadingFrames.FRAME_INTERVAL_MS
            timer.isRepeats = true
            timer.restart()
        } else {
            tick++
            val showElapsed = elapsedMs() >= LoadingFrames.ELAPSED_THRESHOLD_MS
            if (showElapsed != elapsedVisible) {
                elapsedVisible = showElapsed
                current.update()
            } else {
                current.repaint()
            }
        }
    }

    private fun elapsedMs(): Long = (System.nanoTime() - startedAtNanos) / 1_000_000

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater({ Disposer.dispose(this) }, ModalityState.any())
    }

    override fun dispose() {
        stopped.set(true)
        timer.stop()
        inlay?.dispose()
        inlay = null
        if (editor.getUserData(KEY) === this) editor.putUserData(KEY, null)
    }

    private inner class Renderer : EditorCustomElementRenderer {
        private val base: Font = InlineCompletionFontUtils.font(editor)
        private val braille: Boolean = canDisplayBraille(base)
        private val font: Font = if (braille) fontFor(base) else base

        private fun canDisplayBraille(candidate: Font): Boolean = fontFor(candidate).canDisplayUpTo(LoadingFrames.BRAILLE.joinToString("")) == -1

        private fun fontFor(candidate: Font): Font {
            val all = LoadingFrames.BRAILLE.joinToString("")
            return listOf(candidate, Font(Font.MONOSPACED, candidate.style, candidate.size), Font(Font.DIALOG, candidate.style, candidate.size))
                .firstOrNull { it.canDisplayUpTo(all) == -1 } ?: candidate
        }

        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val metrics = editor.contentComponent.getFontMetrics(font)
            val frame = LoadingFrames.frames(braille).maxOf { metrics.stringWidth(it) }
            val elapsed = if (elapsedVisible) metrics.stringWidth(LoadingFrames.WIDEST_ELAPSED) else 0
            return frame + elapsed + GAP
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.font = font
            g.color = InlineCompletionFontUtils.color(editor)
            val ascent = (editor as? EditorImpl)?.ascent ?: g.fontMetrics.ascent
            g.drawString(LoadingFrames.text(tick, elapsedMs(), braille), targetRegion.x + GAP, targetRegion.y + ascent)
        }
    }

    companion object {
        private val KEY = Key.create<InlineLoadingIndicator>("claude.autocomplete.loading.indicator")
        private const val GAP = 6

        fun start(editor: Editor, offset: Int): InlineLoadingIndicator {
            val indicator = InlineLoadingIndicator(editor, offset)
            ApplicationManager.getApplication().invokeLater({
                if (editor.isDisposed || indicator.stopped.get()) return@invokeLater
                editor.getUserData(KEY)?.stop()
                editor.putUserData(KEY, indicator)
                indicator.begin()
            }, ModalityState.any())
            return indicator
        }

        fun isShowing(editor: Editor): Boolean = editor.getUserData(KEY)?.inlay != null
    }
}
