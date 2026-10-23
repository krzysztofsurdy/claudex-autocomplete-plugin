package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import org.jetbrains.yaml.YAMLFileType
import kotlin.time.Duration.Companion.seconds

class LookupClosedIdeTest : GatesIdeTestBase() {
    private fun yamlWithLookup(key: String) = "a: foobar1\nb: foobar2\n$key: foo<caret>\n"

    private fun openLookup() = onEdt { myFixture.completeBasic() }

    private fun cancelLookup() = onEdt { LookupManager.hideActiveLookup(project) }

    private fun pickItem() = onEdt { myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR) }

    private fun lastPrefix(): String = fake.contexts.last().prefix

    fun testOpenPopupAsksForNothing() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("closed1"))
        openLookup()
        delay()
        assertEquals(0, fake.contexts.size)
        assertInlineHidden()
    }

    fun testCancelledPopupAsksForTheCurrentPosition() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("closed2"))
        openLookup()
        cancelLookup()
        delay()
        assertEquals(1, fake.contexts.size)
        assertTrue(lastPrefix().endsWith("closed2: foo"))
        assertInlineElements { gray("world") }
    }

    fun testPickedItemIsFollowedByASuggestionForTheInsertedText() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("closed3"))
        openLookup()
        pickItem()
        delay()
        assertEquals(1, fake.contexts.size)
        assertTrue(lastPrefix().endsWith("closed3: foobar1"))
        assertInlineElements { gray("world") }
    }

    fun testTypingInsideThePopupThenClosingAsksOnceForTheFinalState() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("closed4"))
        openLookup()
        typeChar('b')
        typeChar('a')
        delay()
        assertEquals(0, fake.contexts.size)
        cancelLookup()
        delay()
        assertEquals(1, fake.contexts.size)
        assertTrue(lastPrefix().endsWith("closed4: fooba"))
        assertInlineElements { gray("world") }
    }

    fun testClosingThePopupAfterTypingDoesNotAskTwice() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("closed5"))
        openLookup()
        typeChar('b')
        cancelLookup()
        typeChar('a')
        delay()
        assertEquals(1, fake.contexts.size)
        assertTrue(lastPrefix().endsWith("closed5: fooba"))
    }

    fun testSettingOnLeavesTheBehaviourUnchanged() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.suggestWhileLookupOpen = true
        init(YAMLFileType.YML, yamlWithLookup("closed6"))
        openLookup()
        typeChar('b')
        delay()
        assertEquals(1, fake.contexts.size)
        cancelLookup()
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testSelectionStillBlocksTheRequestAfterThePopupCloses() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("closed7"))
        openLookup()
        onEdt { myFixture.editor.selectionModel.setSelection(0, 5) }
        cancelLookup()
        delay()
        assertEquals(0, fake.contexts.size)
        assertInlineHidden()
    }

    fun testDisabledSettingsAskForNothingAfterThePopupCloses() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.enabled = false
        init(YAMLFileType.YML, yamlWithLookup("closed8"))
        openLookup()
        cancelLookup()
        delay()
        assertEquals(0, fake.contexts.size)
    }

    fun testMidWordPositionStillAsksForNothingAfterThePopupCloses() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "a: foobar1\nb: foobar2\nclosed9: fo<caret>o\n")
        openLookup()
        cancelLookup()
        delay()
        assertEquals(0, fake.contexts.size)
    }
}
