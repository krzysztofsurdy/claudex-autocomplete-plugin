package dev.ksurdy.claudeautocomplete.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteConfigurable
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

class SettingsConfigurableIdeTest : BasePlatformTestCase() {
    private lateinit var configurable: ClaudeAutocompleteConfigurable
    private lateinit var root: JComponent

    override fun setUp() {
        super.setUp()
        ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        configurable = ClaudeAutocompleteConfigurable()
        root = configurable.createComponent()!!
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testMainGroupsExist() {
        (MAIN_GROUPS + ADVANCED).forEach { assertNotNull("missing group $it", titleLabel(it)) }
    }

    fun testGroupsAreOrderedByFeature() {
        val tops = (MAIN_GROUPS + ADVANCED).map { top(titleLabel(it)!!) }
        assertEquals(tops.sorted(), tops)
        assertTrue(top(titleLabel(CLAUDE)!!) > top(titleLabel("Context")!!))
        assertTrue(top(titleLabel(CLAUDE)!!) < top(titleLabel(ADVANCED)!!))
    }

    fun testOnlyTheSelectedProviderShowsItsGroup() {
        assertTrue(isVisible(CLAUDE))
        assertFalse(isVisible(CODEX))
        providerCombo().selectedItem = CODEX
        assertFalse(isVisible(CLAUDE))
        assertTrue(isVisible(CODEX))
        providerCombo().selectedItem = CLAUDE
        assertTrue(isVisible(CLAUDE))
        assertFalse(isVisible(CODEX))
    }

    fun testApplySavesTheProviderAndOtherValues() {
        providerCombo().selectedItem = CODEX
        checkBox("Enable next edit suggestions").isSelected = true
        assertTrue(configurable.isModified)
        configurable.apply()
        val saved = ClaudeAutocompleteSettings.getInstance().state
        assertEquals("codex", saved.provider)
        assertTrue(saved.nextEditSuggestions)
        assertFalse(configurable.isModified)
    }

    fun testApplySavesTheHiddenGroupToo() {
        comboWith("opus").selectedItem = "sonnet"
        comboWith("gpt-5.2").selectedItem = "gpt-5.2"
        providerCombo().selectedItem = CODEX
        configurable.apply()
        val saved = ClaudeAutocompleteSettings.getInstance().state
        assertEquals("sonnet", saved.model)
        assertEquals("gpt-5.2", saved.codexModel)
        assertEquals("codex", saved.provider)
    }

    fun testTestConnectionButtonExists() {
        assertTrue(all(root).filterIsInstance<JButton>().any { it.text == "Test Connection" })
    }

    fun testResetRestoresTheSavedValues() {
        providerCombo().selectedItem = CODEX
        checkBox("Enable next edit suggestions").isSelected = true
        configurable.reset()
        assertEquals(CLAUDE, providerCombo().selectedItem)
        assertFalse(checkBox("Enable next edit suggestions").isSelected)
        assertFalse(configurable.isModified)
        ClaudeAutocompleteSettings.getInstance().state.apply {
            provider = "codex"
            prefetchAfterAccept = false
        }
        configurable.reset()
        assertEquals(CODEX, providerCombo().selectedItem)
        assertFalse(checkBox("Prefetch the next suggestion after accept").isSelected)
    }

    fun testSnippetBudgetsFollowTheirCheckboxes() {
        listOf("Related snippets from open tabs", "Imported classes (PHP)").forEach { text ->
            val box = checkBox(text)
            val field = budgetFieldAfter(box)
            box.isSelected = true
            assertTrue(text, field.isEnabled)
            box.isSelected = false
            assertFalse(text, field.isEnabled)
        }
    }

    private fun providerCombo(): JComboBox<Any> {
        val combo = all(root).filterIsInstance<JComboBox<*>>().first { candidate ->
            (0 until candidate.itemCount).map { candidate.getItemAt(it) }.containsAll(listOf(CLAUDE, CODEX))
        }
        @Suppress("UNCHECKED_CAST")
        return combo as JComboBox<Any>
    }

    private fun comboWith(item: String): JComboBox<Any> {
        val combo = all(root).filterIsInstance<JComboBox<*>>().first { candidate ->
            (0 until candidate.itemCount).any { candidate.getItemAt(it) == item }
        }
        @Suppress("UNCHECKED_CAST")
        return combo as JComboBox<Any>
    }

    private fun checkBox(text: String): JBCheckBox = all(root).filterIsInstance<JBCheckBox>().first { it.text == text }

    private fun budgetFieldAfter(box: JBCheckBox): Component {
        val siblings = box.parent.components.toList()
        return siblings.drop(siblings.indexOf(box) + 1).first { it is JTextField }
    }

    private fun titleLabel(text: String): JLabel? = all(root).filterIsInstance<JLabel>().firstOrNull { it.text == text }

    private fun isVisible(title: String): Boolean {
        var current: Component? = titleLabel(title)
        while (current != null) {
            if (!current.isVisible) return false
            if (current === root) return true
            current = current.parent
        }
        return false
    }

    private fun top(label: JLabel): Int = all(root).indexOf(label)

    private fun all(component: Component): List<Component> =
        listOf(component) + ((component as? Container)?.components?.flatMap { all(it) } ?: emptyList())

    companion object {
        private const val CLAUDE = "Claude Code"
        private const val CODEX = "Codex"
        private const val ADVANCED = "Advanced"
        private val MAIN_GROUPS = listOf("General", "Inline Suggestions", "Next Edit Suggestions", "Context")
    }
}
