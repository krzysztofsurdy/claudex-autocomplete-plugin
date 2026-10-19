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
import kotlinx.coroutines.CompletableDeferred

class NextEditIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine

    private val source = "function foo(\$a)\n{\n    return \$a;\n}\n"
    private val nextEdit = "<edit start_line=\"3\" end_line=\"3\">    return \$ab;</edit>"

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Empty)
        fake.responder = { context -> if (context.nextEdit != null) CompletionResult.Success(nextEdit) else CompletionResult.Empty }
        service.engine = fake
        NextEditService.getInstance().reset()
        ClaudeAutocompleteSettings.getInstance().state.apply {
            debounceMs = 0
            consentGiven = true
            nextEditSuggestions = true
        }
    }

    override fun tearDown() {
        try {
            BackendService.getInstance().engine = original
            StatusService.getInstance().blockUntil(null)
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private fun open(text: String = source, fileName: String = "a.txt"): Editor {
        myFixture.configureByText(fileName, text)
        return myFixture.editor
    }

    private fun typeAfterParameter(editor: Editor, ch: String) {
        editor.caretModel.moveToOffset(editor.document.text.indexOf("\$a)") + 2)
        myFixture.type(ch)
    }

    private fun controller(editor: Editor): NextEditController = NextEditController.of(editor) ?: error("no next edit controller")

    private fun nextEditRequests() = fake.contexts.filter { it.nextEdit != null }

    private fun awaitShown(editor: Editor) =
        PlatformTestUtil.waitWithEventsDispatching("next edit was not shown", { controller(editor).state.isVisible }, 15)

    private fun awaitRequest(count: Int = 1) =
        PlatformTestUtil.waitWithEventsDispatching("no next edit request", { nextEditRequests().size >= count }, 15)

    private fun settle() {
        repeat(10) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(50)
        }
    }

    private fun tab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)

    private fun escape() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)

    private fun caretLine(editor: Editor) = editor.caretModel.logicalPosition.line

    fun testEditShowsASuggestionOnAnotherLineWithoutMovingTheCaret() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        val caret = editor.caretModel.offset
        val shown = controller(editor)
        assertEquals(3, shown.state.edit?.startLine)
        assertEquals("    return \$ab;", shown.state.edit?.newText)
        assertTrue(shown.presenter.isShowing)
        assertEquals(listOf("    return \$ab;"), shown.presenter.previewLines)
        assertEquals("Tab to jump", shown.presenter.hint)
        assertEquals(caret, editor.caretModel.offset)
        assertEquals("function foo(\$ab)\n{\n    return \$a;\n}\n", editor.document.text)
    }

    fun testRequestCarriesTheRecentEditAndTheNumberedFile() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        val input = nextEditRequests().single().nextEdit!!
        assertEquals(listOf(1), input.edits.map { it.startLine })
        assertEquals("function foo(\$a)", input.edits.single().before)
        assertEquals("function foo(\$ab)", input.edits.single().after)
        assertEquals(1, input.caretLine)
        assertEquals(editor.document.text, input.windowText)
    }

    fun testFirstTabJumpsAndSecondTabApplies() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        tab()
        assertEquals(2, caretLine(editor))
        assertEquals(editor.document.getLineStartOffset(2), editor.caretModel.offset)
        assertEquals("function foo(\$ab)\n{\n    return \$a;\n}\n", editor.document.text)
        assertTrue(controller(editor).state.isVisible)
        assertEquals("Tab to apply", controller(editor).presenter.hint)
        tab()
        assertEquals("function foo(\$ab)\n{\n    return \$ab;\n}\n", editor.document.text)
        assertEquals(editor.document.text.indexOf("\$ab;") + 4, editor.caretModel.offset)
        assertFalse(controller(editor).state.isVisible)
        assertFalse(controller(editor).presenter.isShowing)
    }

    fun testAppliedEditIsOneUndoStep() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        tab()
        tab()
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals("function foo(\$ab)\n{\n    return \$a;\n}\n", editor.document.text)
    }

    fun testEscapeDismisses() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        escape()
        assertFalse(controller(editor).state.isVisible)
        assertFalse(controller(editor).presenter.isShowing)
        assertEquals("function foo(\$ab)\n{\n    return \$a;\n}\n", editor.document.text)
    }

    fun testEscapeAfterJumpDismissesAndTabIsNormalAgain() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        tab()
        escape()
        assertFalse(controller(editor).state.isVisible)
        val before = editor.document.text
        tab()
        assertNotSame(before, editor.document.text)
        assertTrue(editor.document.text.length > before.length)
    }

    fun testTypingAnythingElseDismisses() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        fake.nextEditGate = CompletableDeferred()
        myFixture.type("c")
        assertFalse(controller(editor).state.isVisible)
        assertFalse(controller(editor).presenter.isShowing)
    }

    fun testMovingTheCaretDismisses() {
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitShown(editor)
        editor.caretModel.moveToOffset(0)
        assertFalse(controller(editor).state.isVisible)
        assertFalse(controller(editor).presenter.isShowing)
    }

    fun testTabKeepsItsNormalBehaviourWhenNothingIsShown() {
        val editor = open("hello\n")
        editor.caretModel.moveToOffset(5)
        tab()
        assertTrue(editor.document.text, editor.document.text.length > "hello\n".length)
        assertEquals(0, nextEditRequests().size)
    }

    fun testNewTypingCancelsTheInFlightRequestAndAsksAgainForTheLatestText() {
        val editor = open()
        val gate = CompletableDeferred<Unit>()
        fake.nextEditGate = gate
        typeAfterParameter(editor, "b")
        awaitRequest(1)
        myFixture.type("c")
        PlatformTestUtil.waitWithEventsDispatching("request was not cancelled", { fake.cancellations.get() >= 1 }, 15)
        awaitRequest(2)
        assertFalse(controller(editor).state.isVisible)
        gate.complete(Unit)
        awaitShown(editor)
        assertEquals(2, nextEditRequests().size)
        assertEquals("function foo(\$abc)", nextEditRequests().last().nextEdit!!.edits.single().after)
    }

    fun testEditWhileARequestIsInFlightNeverShowsTheOutdatedAnswer() {
        val editor = open()
        val gate = CompletableDeferred<Unit>()
        fake.nextEditGate = gate
        typeAfterParameter(editor, "b")
        awaitRequest(1)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { editor.document.insertString(0, " ") }
        awaitRequest(2)
        assertFalse(controller(editor).state.isVisible)
        gate.complete(Unit)
        awaitShown(editor)
        assertEquals(" function foo(\$ab)", nextEditRequests().last().nextEdit!!.edits.single().after)
        assertEquals(" function foo(\$ab)\n{\n    return \$a;\n}\n", editor.document.text)
    }

    fun testSuggestionOnTheCaretLineIsLeftToInlineCompletion() {
        val editor = open()
        fake.responder = { context ->
            if (context.nextEdit != null) CompletionResult.Success("<edit start_line=\"1\" end_line=\"1\">function foo(\$abc)</edit>") else CompletionResult.Empty
        }
        typeAfterParameter(editor, "b")
        awaitRequest(1)
        settle()
        assertFalse(controller(editor).state.isVisible)
    }

    fun testNoneReplyShowsNothing() {
        val editor = open()
        fake.responder = { context -> if (context.nextEdit != null) CompletionResult.Success("<none/>") else CompletionResult.Empty }
        typeAfterParameter(editor, "b")
        awaitRequest(1)
        settle()
        assertFalse(controller(editor).state.isVisible)
    }

    fun testGarbageReplyShowsNothing() {
        val editor = open()
        fake.responder = { context -> if (context.nextEdit != null) CompletionResult.Success("I think you should rename it") else CompletionResult.Empty }
        typeAfterParameter(editor, "b")
        awaitRequest(1)
        settle()
        assertFalse(controller(editor).state.isVisible)
    }

    fun testDisabledSettingSendsNoRequest() {
        ClaudeAutocompleteSettings.getInstance().state.nextEditSuggestions = false
        val editor = open()
        typeAfterParameter(editor, "b")
        settle()
        assertEquals(0, nextEditRequests().size)
        assertFalse(controller(editor).state.isVisible)
        assertTrue(controller(editor).history.hunks().isEmpty())
    }

    fun testNoRequestWithoutConsent() {
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = false
        val editor = open()
        typeAfterParameter(editor, "b")
        settle()
        assertEquals(0, fake.contexts.size)
        assertFalse(controller(editor).state.isVisible)
    }

    fun testNoRequestWhenCompletionsAreDisabled() {
        ClaudeAutocompleteSettings.getInstance().state.enabled = false
        val editor = open()
        typeAfterParameter(editor, "b")
        settle()
        assertEquals(0, fake.contexts.size)
    }

    fun testSecretFilesAreNeitherTrackedNorSent() {
        val editor = open("API_KEY=abc\nOTHER=\$API_KEY\n", ".env")
        editor.caretModel.moveToOffset(11)
        myFixture.type("d")
        settle()
        assertEquals(0, fake.contexts.size)
        assertTrue(controller(editor).history.hunks().isEmpty())
    }

    fun testCustomExcludedPatternsApply() {
        ClaudeAutocompleteSettings.getInstance().state.excludedFilePatterns = "private.*"
        val editor = open(fileName = "private.txt")
        typeAfterParameter(editor, "b")
        settle()
        assertEquals(0, fake.contexts.size)
    }

    fun testDisabledLanguagesAreSkipped() {
        ClaudeAutocompleteSettings.getInstance().state.disabledLanguages = "TEXT"
        val editor = open()
        typeAfterParameter(editor, "b")
        settle()
        assertEquals(0, nextEditRequests().size)
    }

    fun testRateLimitedFailureShowsNothingAndBlocksFurtherRequests() {
        val editor = open()
        fake.responder = { context ->
            if (context.nextEdit != null) CompletionResult.Failure(dev.ksurdy.claudeautocomplete.backend.FailureKind.RateLimited, "limit") else CompletionResult.Empty
        }
        typeAfterParameter(editor, "b")
        awaitRequest(1)
        PlatformTestUtil.waitWithEventsDispatching("not blocked", { StatusService.getInstance().isBlocked(java.time.Instant.now()) }, 15)
        assertFalse(controller(editor).state.isVisible)
        myFixture.type("c")
        settle()
        assertEquals(1, nextEditRequests().size)
    }

    fun testRequestsFollowTheConfiguredProvider() {
        ClaudeAutocompleteSettings.getInstance().state.provider = "codex"
        val editor = open()
        typeAfterParameter(editor, "b")
        awaitRequest(1)
        assertEquals(dev.ksurdy.claudeautocomplete.backend.ProviderKind.Codex, fake.configs.last().provider)
    }
}
