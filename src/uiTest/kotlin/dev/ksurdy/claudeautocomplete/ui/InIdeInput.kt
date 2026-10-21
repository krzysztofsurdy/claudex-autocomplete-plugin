package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.remote.Component

@Remote("com.intellij.openapi.actionSystem.DataContext")
interface DataContext

@Remote("com.intellij.ide.DataManager")
interface DataManager {
    fun getInstance(): DataManager

    fun getDataContext(component: Component): DataContext
}

@Remote("com.intellij.openapi.editor.actionSystem.TypedAction")
interface TypedAction {
    fun getInstance(): TypedAction

    fun actionPerformed(editor: Editor, charTyped: Char, dataContext: DataContext)
}

fun JEditorUiComponent.typeCharacter(driver: Driver, character: Char) {
    val target = editor
    val source = component
    driver.withContext(OnDispatcher.EDT, LockSemantics.WRITE_ACTION) {
        val dataContext = utility(DataManager::class).getInstance().getDataContext(source)
        utility(TypedAction::class).getInstance().actionPerformed(target, character, dataContext)
    }
}

fun JEditorUiComponent.replaceDocument(driver: Driver, newText: String, caretOffset: Int) {
    val target = editor
    driver.withWriteAction { target.getDocument().setText(newText) }
    moveCaretToOffset(caretOffset)
}
