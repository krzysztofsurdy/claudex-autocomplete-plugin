package dev.ksurdy.claudeautocomplete.ide

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.nextedit.NextEditController
import dev.ksurdy.claudeautocomplete.nextedit.NextEditService
import java.util.concurrent.CopyOnWriteArrayList

class NextEditTimingIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine

    @Volatile
    private var now = 1_000L
    private val sleeps = CopyOnWriteArrayList<Long>()

    @Volatile
    private var reply = "    return \$ab;"

    private val source = "function foo(\$a)\n{\n    return \$a;\n}\n"

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Empty)
        fake.responder = { context ->
            if (context.nextEdit != null) CompletionResult.Success("<edit start_line=\"3\" end_line=\"3\">$reply</edit>") else CompletionResult.Empty
        }
        service.engine = fake
        NextEditService.getInstance().reset({ now }, { ms -> sleeps += ms })
        ClaudeAutocompleteSettings.getInstance().state.apply {
            debounceMs = 100
            consentGiven = true
            nextEditSuggestions = true
        }
    }

    override fun tearDown() {
        try {
            BackendService.getInstance().engine = original
            NextEditService.getInstance().reset()
            StatusService.getInstance().blockUntil(null)
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private fun open(text: String = source): Editor {
        myFixture.configureByText("a.txt", text)
        return myFixture.editor
    }

    private fun typeInParameter(editor: Editor, ch: String) {
        editor.caretModel.moveToOffset(editor.document.text.indexOf(")"))
        myFixture.type(ch)
    }

    private fun controller(editor: Editor): NextEditController = NextEditController.of(editor) ?: error("no next edit controller")

    private fun requests() = fake.contexts.filter { it.nextEdit != null }

    private fun awaitShown(editor: Editor) =
        PlatformTestUtil.waitWithEventsDispatching("next edit was not shown", { controller(editor).state.isVisible }, 15)

    private fun awaitRequests(count: Int) =
        PlatformTestUtil.waitWithEventsDispatching("expected $count next edit requests, got ${requests().size}", { requests().size >= count }, 15)

    private fun settle() {
        repeat(10) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(50)
        }
    }

    private fun escape() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)

    private fun tab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)

    private fun showThenReject(editor: Editor) {
        typeInParameter(editor, "b")
        awaitShown(editor)
        escape()
    }

    fun testFirstPauseIsTwiceTheDebounce() {
        val editor = open()
        typeInParameter(editor, "b")
        awaitRequests(1)
        assertEquals(listOf(200L), sleeps.toList())
    }

    fun testRejectionLengthensAndAcceptanceShortensThePause() {
        val editor = open()
        showThenReject(editor)
        now += 6_000
        typeInParameter(editor, "c")
        awaitRequests(2)
        assertEquals(300L, sleeps.last())
        now += 400_000
        typeInParameter(editor, "d")
        awaitShown(editor)
        tab()
        tab()
        typeInParameter(editor, "e")
        awaitRequests(4)
        assertEquals(240L, sleeps.last())
    }

    fun testRejectionBlocksRequestsForFiveSeconds() {
        val editor = open()
        showThenReject(editor)
        typeInParameter(editor, "c")
        settle()
        assertEquals(1, requests().size)
        now += 4_999
        typeInParameter(editor, "d")
        settle()
        assertEquals(1, requests().size)
        now += 1
        typeInParameter(editor, "e")
        awaitRequests(2)
    }

    fun testTypingElsewhereWhileShownCountsAsRejection() {
        val editor = open()
        typeInParameter(editor, "b")
        awaitShown(editor)
        myFixture.type("c")
        settle()
        assertEquals(1, requests().size)
        assertFalse(controller(editor).state.isVisible)
    }

    fun testRejectedLineGetsNoOtherSuggestionForThirtySeconds() {
        val editor = open()
        showThenReject(editor)
        now += 6_000
        reply = "    return \$abc;"
        typeInParameter(editor, "c")
        awaitRequests(2)
        settle()
        assertFalse(controller(editor).state.isVisible)
        now += 25_000
        typeInParameter(editor, "d")
        awaitRequests(3)
        awaitShown(editor)
        assertEquals("    return \$abc;", controller(editor).state.edit?.newText)
    }

    fun testRejectedSuggestionIsNeverShownAgainInTheSameFile() {
        val editor = open()
        showThenReject(editor)
        now += 60_000
        typeInParameter(editor, "c")
        awaitRequests(2)
        settle()
        assertFalse(controller(editor).state.isVisible)
        now += 100_000
        typeInParameter(editor, "d")
        awaitRequests(3)
        settle()
        assertFalse(controller(editor).state.isVisible)
        now += 200_000
        typeInParameter(editor, "e")
        awaitRequests(4)
        awaitShown(editor)
    }

    fun testEndOfLineAddsTwoSecondsOfWaiting() {
        val editor = open("foo\n")
        editor.caretModel.moveToOffset(3)
        myFixture.type("x")
        awaitRequests(1)
        assertEquals(listOf(200L, 2_000L), sleeps.toList())
    }

    fun testMidLineAddsNoExtraWaiting() {
        val editor = open()
        typeInParameter(editor, "b")
        awaitRequests(1)
        assertEquals(listOf(200L), sleeps.toList())
    }

    fun testMovingToAnotherLineSoonAfterAnEditAsksAgain() {
        val editor = open()
        typeInParameter(editor, "b")
        awaitShown(editor)
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(3))
        awaitRequests(2)
        awaitShown(editor)
    }

    fun testMovingTheCaretLongAfterTheEditAsksNothing() {
        val editor = open()
        typeInParameter(editor, "b")
        awaitShown(editor)
        now += 10_001
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(3))
        settle()
        assertEquals(1, requests().size)
    }

    fun testMovingWithinTheSameLineAsksNothing() {
        val editor = open()
        typeInParameter(editor, "b")
        awaitShown(editor)
        editor.caretModel.moveToOffset(2)
        settle()
        assertEquals(1, requests().size)
    }

    fun testMovingTheCaretWithoutAnEditAsksNothing() {
        val editor = open()
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(3))
        settle()
        assertEquals(0, requests().size)
    }

    fun testCaretMoveObeysTheRejectionCooldown() {
        val editor = open()
        showThenReject(editor)
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(3))
        settle()
        assertEquals(1, requests().size)
    }
}
