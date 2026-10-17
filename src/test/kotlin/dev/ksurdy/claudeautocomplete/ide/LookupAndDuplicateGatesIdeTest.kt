package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.yaml.YAMLFileType
import kotlin.time.Duration.Companion.seconds

class LookupAndDuplicateGatesIdeTest : GatesIdeTestBase() {
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

    fun testRepeatedEventForTheSameStateStartsNoSecondRequest() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        init(PlainTextFileType.INSTANCE, "gate15 <caret>")
        onEdt {
            insertAtCaret()
            handler().invokeEvent(typingEvent())
        }
        repeat(100) { if (fake.contexts.isEmpty()) delay(50) }
        assertEquals(1, fake.contexts.size)
        onEdt { handler().invokeEvent(typingEvent()) }
        gate.complete(Unit)
        delay()
        assertEquals(1, fake.contexts.size)
    }

    fun testANewEditAfterARequestIsNotADuplicate() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate16<caret>")
        fireAutomatic()
        delay()
        typeChar(' ')
        delay()
        assertEquals(2, fake.contexts.size)
    }

    fun testOneEventAnsweredTwiceByTheProviderIsNotItsOwnDuplicate() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate17 <caret>")
        val provider = ClaudeInlineCompletionProvider()
        var first = false
        var again = false
        var repeated = true
        onEdt {
            val event = typingEvent()
            first = provider.isEnabled(event)
            again = provider.isEnabled(event)
            repeated = provider.isEnabled(typingEvent())
        }
        assertTrue(first)
        assertTrue(again)
        assertFalse(repeated)
    }

    fun testManualTriggerIsNeverADuplicate() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "gate18 <caret>")
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        escape()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(2, fake.contexts.size)
    }
}
