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
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Timer

class InlineLoadingIndicator private constructor(private val editor: Editor, private val offset: Int) : Disposable {
    private val stopped = AtomicBoolean(false)
    private var inlay: Inlay<*>? = null
    private var tick = 0
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
            current.repaint()
        }
    }

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
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val font = InlineCompletionFontUtils.font(editor)
            return editor.contentComponent.getFontMetrics(font).stringWidth(LoadingFrames.widest) + GAP
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            val font = InlineCompletionFontUtils.font(editor)
            g.font = font
            g.color = InlineCompletionFontUtils.color(editor)
            val ascent = (editor as? EditorImpl)?.ascent ?: g.fontMetrics.ascent
            g.drawString(LoadingFrames.frame(tick), targetRegion.x + GAP, targetRegion.y + ascent)
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
