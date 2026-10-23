package dev.ksurdy.claudeautocomplete.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher

@Remote("com.intellij.openapi.editor.ex.EditorSettingsExternalizable")
interface EditorSettingsHandle {
    fun getInstance(): EditorSettingsHandle

    fun setStickyLinesShown(shown: Boolean): Boolean

    fun setBlinkCaret(blink: Boolean)
}

@Remote("com.intellij.codeInsight.codeVision.settings.CodeVisionSettings")
interface CodeVisionHandle {
    fun setCodeVisionEnabled(enabled: Boolean)
}

object MediaLook {
    fun apply(driver: Driver) {
        driver.withContext(OnDispatcher.EDT) {
            val settings = utility(EditorSettingsHandle::class).getInstance()
            settings.setStickyLinesShown(false)
            settings.setBlinkCaret(false)
            service(CodeVisionHandle::class).setCodeVisionEnabled(false)
        }
    }
}
