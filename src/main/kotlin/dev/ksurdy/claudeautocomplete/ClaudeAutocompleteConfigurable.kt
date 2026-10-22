package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.NamedColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.intellij.ui.dsl.builder.panel
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import kotlinx.coroutines.runBlocking
import dev.ksurdy.claudeautocomplete.settings.advancedGroup
import dev.ksurdy.claudeautocomplete.settings.contextGroup
import dev.ksurdy.claudeautocomplete.settings.generalGroup
import dev.ksurdy.claudeautocomplete.settings.inlineSuggestionsGroup
import dev.ksurdy.claudeautocomplete.settings.nextEditGroup
import dev.ksurdy.claudeautocomplete.settings.providerGroups
import javax.swing.JButton

class ClaudeAutocompleteConfigurable :
    BoundSearchableConfigurable(ClaudexBundle.message("settings.display.name"), SETTINGS_ID, SETTINGS_ID) {

    private val persisted get() = ClaudeAutocompleteSettings.getInstance().state
    private val working = ClaudeAutocompleteSettings.State()
    private var panelRef: DialogPanel? = null

    override fun reset() {
        working.copyFrom(persisted)
        super.reset()
    }

    override fun isModified(): Boolean = super.isModified() || working != persisted

    override fun apply() {
        super.apply()
        val providerBefore = persisted.providerKind()
        persisted.copyFrom(working)
        if (persisted.providerKind() != providerBefore) BackendService.getInstance().providerChanged()
        StatusService.getInstance().refresh()
    }

    override fun createPanel(): DialogPanel {
        working.copyFrom(persisted)
        val state = working
        val result = JBLabel(" ")
        return panel {
            val providerCombo = generalGroup(state, result) { runTest(result, it) }
            inlineSuggestionsGroup(state)
            nextEditGroup(state)
            contextGroup(state)
            providerGroups(state, providerCombo)
            advancedGroup(state)
        }.also { panelRef = it }
    }

    private fun runTest(label: JBLabel, button: JButton) {
        panelRef?.apply()
        button.isEnabled = false
        label.foreground = UIUtil.getLabelForeground()
        label.text = ClaudexBundle.message("settings.test.running")
        val config = working.toBackendConfig()
        ApplicationManager.getApplication().executeOnPooledThread {
            val started = System.nanoTime()
            val outcome = runBlocking {
                BackendService.getInstance().ping(config).also { BackendService.getInstance().refreshUsage(config) }
            }
            val millis = (System.nanoTime() - started) / 1_000_000
            val failure = outcome as? CompletionResult.Failure
            if (failure != null) LOG.warn("Test connection failed: kind=${failure.kind} message=${failure.message.take(MAX_LOGGED_MESSAGE)}")
            val text = when (outcome) {
                is CompletionResult.Success -> ClaudexBundle.message("settings.test.ok", millis, outcome.text.take(80).replace('\n', ' '))
                CompletionResult.Empty -> ClaudexBundle.message("settings.test.ok.empty", millis)
                is CompletionResult.Failure -> ClaudexBundle.message("settings.test.failed", outcome.kind.label(), outcome.message.take(200))
            }
            ApplicationManager.getApplication().invokeLater({
                label.foreground = if (failure != null) NamedColorUtil.getErrorForeground() else UIUtil.getLabelForeground()
                label.text = text
                button.isEnabled = true
            }, ModalityState.any())
        }
    }

    companion object {
        const val SETTINGS_ID = "dev.ksurdy.claudeautocomplete.settings"
        private const val MAX_LOGGED_MESSAGE = 300
        private val LOG = Logger.getInstance(ClaudeAutocompleteConfigurable::class.java)
    }
}
