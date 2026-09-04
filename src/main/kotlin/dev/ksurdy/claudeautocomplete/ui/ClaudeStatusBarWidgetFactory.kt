package dev.ksurdy.claudeautocomplete.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteConfigurable
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.activeModel
import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import dev.ksurdy.claudeautocomplete.displayName
import dev.ksurdy.claudeautocomplete.providerKind
import dev.ksurdy.claudeautocomplete.toBackendConfig
import dev.ksurdy.claudeautocomplete.StatusFormatter
import dev.ksurdy.claudeautocomplete.StatusService
import kotlinx.coroutines.runBlocking
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import javax.swing.Timer

class ClaudeStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ClaudeStatusBarWidget.ID

    override fun getDisplayName(): String = "Claude Autocomplete"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = ClaudeStatusBarWidget(project)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class ClaudeStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private val ticker = Timer(TICK_MS) { onStatusChanged() }.apply { isRepeats = true }
    private val usageTimer = Timer(USAGE_CHECK_MS) { BackendService.getInstance().refreshUsageIfStale() }
        .apply { isRepeats = true }
    private val listener: () -> Unit = {
        ApplicationManager.getApplication().invokeLater({ onStatusChanged() }, ModalityState.any())
    }

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        StatusService.getInstance().addListener(listener)
        usageTimer.start()
        BackendService.getInstance().refreshUsageIfStale()
    }

    override fun dispose() {
        ticker.stop()
        usageTimer.stop()
        StatusService.getInstance().removeListener(listener)
        statusBar = null
    }

    override fun getText(): String {
        val state = settings().state
        return StatusFormatter.widgetText(
            enabled = state.enabled,
            status = StatusService.getInstance().status,
            usage = StatusService.getInstance().usage,
            showUsage = state.showUsageInStatusBar,
            nowMs = System.currentTimeMillis(),
            providerName = state.providerKind().displayName(),
        )
    }

    override fun getAlignment(): Float = 0.5f

    override fun getTooltipText(): String {
        val service = StatusService.getInstance()
        val state = settings().state
        return StatusFormatter.tooltip(
            enabled = state.enabled,
            status = service.status,
            usage = service.usage,
            lastLatencyMs = service.lastLatencyMs,
            model = state.toBackendConfig().activeModel(),
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
            providerName = state.providerKind().displayName(),
        )
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event -> showMenu(event) }

    private fun onStatusChanged() {
        val service = StatusService.getInstance()
        if (StatusFormatter.isAnimating(settings().state.enabled, service.status, System.currentTimeMillis())) ticker.start() else ticker.stop()
        statusBar?.updateWidget(ID)
    }

    private fun showMenu(event: MouseEvent) {
        val bar = statusBar ?: return
        val enabled = settings().state.enabled
        val group = DefaultActionGroup(
            action(if (enabled) "Disable Claude Autocomplete" else "Enable Claude Autocomplete") {
                settings().state.enabled = !enabled
                StatusService.getInstance().refresh()
            },
            DefaultActionGroup("Provider", true).apply {
                ProviderKind.entries.forEach { add(providerAction(it)) }
            },
            action("Open Settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, ClaudeAutocompleteConfigurable::class.java)
            },
            action("Refresh Usage") { refreshUsage() },
        )
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                null,
                group,
                DataManager.getInstance().getDataContext(event.component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false,
            )
            .show(RelativePoint(event))
        bar.updateWidget(ID)
    }

    private fun refreshUsage() {
        val config = settings().toBackendConfig()
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking { BackendService.getInstance().refreshUsage(config) }
        }
    }

    private fun providerAction(kind: ProviderKind): AnAction = object : DumbAwareToggleAction(kind.displayName()) {
        override fun getActionUpdateThread() = ActionUpdateThread.BGT

        override fun isSelected(e: AnActionEvent): Boolean = settings().state.providerKind() == kind

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (!state || settings().state.providerKind() == kind) return
            settings().state.provider = if (kind == ProviderKind.Codex) "codex" else "claude"
            BackendService.getInstance().providerChanged()
        }
    }

    private fun action(text: String, perform: () -> Unit): AnAction = object : DumbAwareAction(text) {
        override fun actionPerformed(e: AnActionEvent) = perform()
    }

    private fun settings() = ClaudeAutocompleteSettings.getInstance()

    companion object {
        const val ID = "ClaudeAutocompleteStatus"
        private const val TICK_MS = 500
        private const val USAGE_CHECK_MS = 60_000
    }
}
