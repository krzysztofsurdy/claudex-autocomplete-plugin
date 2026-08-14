package dev.ksurdy.claudeautocomplete.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.StatusService
import java.awt.event.MouseEvent

class ClaudeStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ClaudeStatusBarWidget.ID

    override fun getDisplayName(): String = "Claude Autocomplete"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = ClaudeStatusBarWidget()

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class ClaudeStatusBarWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private val listener: () -> Unit = {
        ApplicationManager.getApplication().invokeLater({ statusBar?.updateWidget(ID) }, ModalityState.any())
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        StatusService.getInstance().addListener(listener)
    }

    override fun dispose() {
        StatusService.getInstance().removeListener(listener)
        statusBar = null
    }

    override fun getText(): String = StatusService.getInstance().displayText(settings().state.enabled)

    override fun getAlignment(): Float = 0.5f

    override fun getTooltipText(): String = "Click to toggle Claude autocomplete"

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val state = settings().state
        state.enabled = !state.enabled
        StatusService.getInstance().refresh()
    }

    private fun settings() = ClaudeAutocompleteSettings.getInstance()

    companion object {
        const val ID = "ClaudeAutocompleteStatus"
    }
}
