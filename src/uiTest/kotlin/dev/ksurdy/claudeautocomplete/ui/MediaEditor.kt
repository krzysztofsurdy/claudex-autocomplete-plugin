package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

@Remote("com.intellij.openapi.editor.ScrollingModel")
interface ScrollingModelHandle {
    fun disableAnimation()

    fun scrollVertically(offset: Int)
}

@Remote("com.intellij.openapi.editor.Editor")
interface ScrollableEditor {
    fun getScrollingModel(): ScrollingModelHandle

    fun getLineHeight(): Int

    fun visualLineToY(line: Int): Int
}

fun JEditorUiComponent.scrollOffsetToRow(driver: Driver, offset: Int, row: Int) {
    interact {
        val visualLine = offsetToVisualPosition(offset).getLine()
        val editor = driver.cast(this, ScrollableEditor::class)
        val scrolling = editor.getScrollingModel()
        scrolling.disableAnimation()
        scrolling.scrollVertically((editor.visualLineToY(visualLine) - row * editor.getLineHeight()).coerceAtLeast(0))
    }
}

fun JEditorUiComponent.shownText(): String {
    val hints = getInlineCompletion().joinToString("") { it.text }
    val trailing = getAfterLineHints(getCaretLine()).joinToString("")
    return hints + trailing
}
