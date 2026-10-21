package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent

@Remote("com.intellij.openapi.editor.InlayModel")
interface BlockInlayModel {
    fun getBlockElementsInRange(startOffset: Int, endOffset: Int): List<BlockInlay>
}

@Remote("com.intellij.openapi.editor.EditorCustomElementRenderer")
interface BlockRenderer

@Remote("com.intellij.openapi.editor.Inlay")
interface BlockInlay {
    fun getOffset(): Int

    fun getRenderer(): BlockRenderer
}

@Remote("dev.ksurdy.claudeautocomplete.nextedit.NextEditPresenter\$PreviewRenderer", plugin = "dev.ksurdy.claudeautocomplete")
interface PreviewRenderer {
    fun getLines(): List<String>

    fun getHint(): String
}

class NextEditPreview(val offset: Int, val line: Int, val lines: List<String>, val hint: String) {
    val text: String get() = lines.joinToString("\n")
}

fun JEditorUiComponent.nextEditPreview(driver: Driver): NextEditPreview? = interact {
    val model = driver.cast(getInlayModel(), BlockInlayModel::class)
    model.getBlockElementsInRange(0, Int.MAX_VALUE).firstNotNullOfOrNull { inlay ->
        runCatching {
            val renderer = driver.cast(inlay.getRenderer(), PreviewRenderer::class)
            val offset = inlay.getOffset()
            NextEditPreview(offset, getDocument().getLineNumber(offset), renderer.getLines(), renderer.getHint())
        }.getOrNull()
    }
}

fun JEditorUiComponent.caretOffset(): Int = interact { getCaretModel().getOffset() }

fun JEditorUiComponent.lineOf(offset: Int): Int = interact { getDocument().getLineNumber(offset) }
