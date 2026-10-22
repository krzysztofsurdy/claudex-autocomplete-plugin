package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import org.jetbrains.yaml.YAMLFileType
import kotlin.time.Duration.Companion.seconds

class LookupGateIdeTest : GatesIdeTestBase() {
    private fun openLookup() = onEdt { myFixture.completeBasic() }

    private fun yamlWithLookup(key: String) = "a: foobar1\nb: foobar2\n$key: foo<caret>\n"

    fun testOpenLookupBlocksAutomaticRequestsByDefault() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("lookup1"))
        openLookup()
        typeChar('b')
        delay()
        assertEquals(0, fake.contexts.size)
        assertInlineHidden()
    }

    fun testOpenLookupAllowsAutomaticRequestsWhenTheSettingIsOn() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.suggestWhileLookupOpen = true
        init(YAMLFileType.YML, yamlWithLookup("lookup2"))
        openLookup()
        typeChar('b')
        delay()
        assertEquals(1, fake.contexts.size)
        assertInlineElements { gray("world") }
    }

    fun testOpenLookupStillAllowsManualTriggerByDefault() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("lookup3"))
        openLookup()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testOpenLookupStillAllowsDirectCallByDefault() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("lookup4"))
        openLookup()
        callInlineCompletion()
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testTypingAfterTheLookupClosesRequestsAgain() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, yamlWithLookup("lookup5"))
        openLookup()
        typeChar('b')
        delay()
        assertEquals(0, fake.contexts.size)
        onEdt { LookupManager.hideActiveLookup(project) }
        typeChar('a')
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testRepeatedEventForTheSameStateStillShowsASuggestion() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate15 <caret>")
        onEdt {
            insertAtCaret()
            handler().invokeEvent(typingEvent())
            handler().invokeEvent(typingEvent())
        }
        delay()
        assertInlineElements { gray("world") }
    }

    fun testManualTriggerAfterAnAutomaticRequestForTheSameStateStillRuns() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate18 <caret>")
        fireAutomatic()
        delay()
        escape()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertInlineElements { gray("world") }
    }
}
