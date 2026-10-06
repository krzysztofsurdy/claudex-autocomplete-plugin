package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.jetbrains.php.lang.PhpFileType
import org.jetbrains.yaml.YAMLFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.StatusFormatter
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.backend.ProviderKind
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.backend.FailureKind
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.seconds

class ClaudeInlineCompletionIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Success("world"))
        service.engine = fake
        ClaudeAutocompleteSettings.getInstance().state.debounceMs = 0
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = true
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

    fun testDirectCallShowsGrayTextAndTabInserts() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "hello0 <caret>")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
        insert()
        assertFileContent("hello0 world<caret>")
    }

    fun testTypingTriggersCompletion() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "hello1<caret>")
        typeChar(' ')
        delay()
        assertInlineElements { gray("world") }
    }

    fun testMidLineWithCodeRightOfCaretIsSuppressed() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "foo(<caret>bar)")
        callInlineCompletion()
        delay()
        assertInlineHidden()
        assertEquals(0, fake.contexts.size)
    }

    fun testClosingCharsRightOfCaretStillTrigger() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "foo(<caret>)")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
        assertEquals(1, fake.contexts.size)
    }

    fun testContextCarriesPrefixSuffixAndPath() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "first line\nhello <caret>\nlast line")
        callInlineCompletion()
        delay()
        val context = fake.contexts.single()
        assertEquals("first line\nhello ", context.prefix)
        assertEquals("\nlast line", context.suffix)
        assertTrue(context.filePath, context.filePath.endsWith(".txt"))
        assertEquals("TEXT", context.languageId)
    }

    fun testTypingMatchingCharKeepsAndTrimsSuggestion() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "hello5 <caret>")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
        typeChar('w')
        delay()
        assertInlineRender("orld")
        assertEquals(1, fake.contexts.size)
    }

    fun testTypingNonMatchingCharDropsSuggestion() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "hello6 <caret>")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
        typeChar('z')
        delay()
        assertFileContent("hello6 z<caret>")
    }

    fun testFailureShowsNothing() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        fake.result = CompletionResult.Empty
        init(PlainTextFileType.INSTANCE, "hello7 <caret>")
        callInlineCompletion()
        delay()
        assertInlineHidden()
    }

    fun testEscapeDismisses() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "hello8 <caret>")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
        escape()
        assertInlineHidden()
    }

    fun testOpenTabsAreIncludedInContext() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        withWriteAction {
            val other = fixture.addFileToProject("other.txt", "other tab content").virtualFile
            FileEditorManager.getInstance(fixture.project).openFile(other, false)
        }
        init(PlainTextFileType.INSTANCE, "tabs9 <caret>")
        callInlineCompletion()
        delay()
        val context = fake.contexts.single()
        assertEquals(listOf("other tab content"), context.openFiles.map { it.content })
    }

    fun testOpenTabsAreOrderedMostRecentFirst() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        withWriteAction {
            val manager = FileEditorManager.getInstance(fixture.project)
            listOf("first", "second", "third").forEach {
                manager.openFile(fixture.addFileToProject("$it.txt", it).virtualFile, false)
            }
        }
        init(PlainTextFileType.INSTANCE, "order9 <caret>")
        callInlineCompletion()
        delay()
        assertEquals(listOf("third", "second", "first"), fake.contexts.single().openFiles.map { it.content })
    }

    fun testSecretFilesAreNotSentAsOpenTabs() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        withWriteAction {
            val manager = FileEditorManager.getInstance(fixture.project)
            manager.openFile(fixture.addFileToProject(".env", "API_KEY=abc").virtualFile, false)
            manager.openFile(fixture.addFileToProject("ok.txt", "fine").virtualFile, false)
        }
        init(PlainTextFileType.INSTANCE, "secret1 <caret>")
        callInlineCompletion()
        delay()
        assertEquals(listOf("fine"), fake.contexts.single().openFiles.map { it.content })
    }

    fun testCurrentSecretFileSkipsCompletion() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        myFixture.configureByText(".env", "TOKEN=<caret>")
        callInlineCompletion()
        delay()
        assertTrue(fake.contexts.isEmpty())
    }

    fun testCustomExcludedPatternsApply() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.excludedFilePatterns = "private.*"
        withWriteAction {
            val manager = FileEditorManager.getInstance(fixture.project)
            manager.openFile(fixture.addFileToProject("private.txt", "hidden").virtualFile, false)
            manager.openFile(fixture.addFileToProject("public.txt", "shown").virtualFile, false)
        }
        init(PlainTextFileType.INSTANCE, "custom1 <caret>")
        callInlineCompletion()
        delay()
        assertEquals(listOf("shown"), fake.contexts.single().openFiles.map { it.content })
    }

    fun testManualTriggerIgnoresMidLineRule() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "manual10(<caret>bar)")
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertInlineElements { gray("world") }
        assertEquals(1, fake.contexts.size)
    }

    fun testManualTriggerBypassesCache() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "manual11 <caret>")
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        escape()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(2, fake.contexts.size)
    }

    fun testContextReportsTruncationAndIndent() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.linesAroundCursor = 1
        init(PlainTextFileType.INSTANCE, "a\nb\nc\nd\n" + "trunc12 <caret>\ne\nf\ng\nh")
        ClaudeAutocompleteSettings.getInstance().state.wholeFileMaxLines = 3
        callInlineCompletion()
        delay()
        val context = fake.contexts.single()
        assertTrue(context.prefixTruncated)
        assertTrue(context.suffixTruncated)
        assertTrue(context.indent, context.indent == "tabs" || context.indent.endsWith(" spaces"))
    }

    fun testToggleActionRefreshesStatusListeners() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "toggle13 <caret>")
        var refreshes = 0
        val listener = { refreshes++; Unit }
        StatusService.getInstance().addListener(listener)
        try {
            callAction("ClaudeAutocomplete.Toggle")
            assertFalse(ClaudeAutocompleteSettings.getInstance().state.enabled)
            assertEquals(1, refreshes)
            assertEquals("Claude: Off", StatusFormatter.widgetText(false, StatusService.getInstance().status, null, true, 0))
        } finally {
            StatusService.getInstance().removeListener(listener)
        }
    }

    fun testNoBackendCallWithoutConsent() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = false
        init(PlainTextFileType.INSTANCE, "noconsent <caret>")
        callInlineCompletion()
        delay()
        assertTrue(fake.contexts.isEmpty())
    }

    fun testSuccessReportsDoneWithLatency() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        init(PlainTextFileType.INSTANCE, "done14 <caret>")
        callInlineCompletion()
        delay()
        assertTrue(StatusService.getInstance().status.toString(), StatusService.getInstance().status is ClaudeStatus.Done)
        assertNotNull(StatusService.getInstance().lastLatencyMs)
    }

    fun testRateLimitBlocksFurtherRequestsUntilReset() = myFixture.testInlineCompletion(timeout = 20.seconds) {
        fake.result = CompletionResult.Failure(FailureKind.RateLimited, "limit")
        init(PlainTextFileType.INSTANCE, "limit15 <caret>")
        callInlineCompletion()
        delay()
        assertTrue(StatusService.getInstance().status.toString(), StatusService.getInstance().status is ClaudeStatus.LimitReached)
        assertTrue(StatusService.getInstance().isBlocked(java.time.Instant.now()))
        escape()
        callAction("ClaudeAutocomplete.Trigger")
        delay()
        assertEquals(1, fake.contexts.size)
        assertTrue(StatusService.getInstance().status is ClaudeStatus.LimitReached)
    }

    private fun loadingInlays(): Int {
        var count = 0
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
            val editor = myFixture.editor
            count = editor.inlayModel.getAfterLineEndElementsInRange(0, editor.document.textLength).size
        }
        return count
    }

    private suspend fun com.intellij.codeInsight.inline.completion.InlineCompletionLifecycleTestDSL.awaitLoadingInlays(expected: Int) {
        repeat(60) {
            if (loadingInlays() == expected) return
            delay(50)
        }
        assertEquals(expected, loadingInlays())
    }

    fun testLoadingIndicatorShownWhileInFlightAndRemovedOnCompletion() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        init(PlainTextFileType.INSTANCE, "loading16 <caret>")
        callInlineCompletion()
        awaitLoadingInlays(1)
        gate.complete(Unit)
        delay()
        assertInlineElements { gray("world") }
        awaitLoadingInlays(0)
    }

    fun testLoadingIndicatorRemovedOnCancel() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        fake.gate = CompletableDeferred()
        init(PlainTextFileType.INSTANCE, "loading17 <caret>")
        callInlineCompletion()
        awaitLoadingInlays(1)
        escape()
        typeChar('q')
        awaitLoadingInlays(0)
    }

    fun testLoadingIndicatorCanBeDisabled() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.showInlineLoadingIndicator = false
        fake.gate = CompletableDeferred()
        init(PlainTextFileType.INSTANCE, "loading18 <caret>")
        callInlineCompletion()
        delay(600)
        assertEquals(0, loadingInlays())
        fake.gate?.complete(Unit)
    }

    private fun addPhpDeps() {
        myFixture.addFileToProject(
            "Base.php",
            "<?php\nnamespace App;\ninterface Marker {}\n" +
                "abstract class Base {\n public const X = 1;\n protected int \$count = 0;\n private int \$secret = 1;\n" +
                " abstract public function run(int \$a, ?string \$b = null): bool;\n" +
                " private function hidden() {}\n public static function make(): static {}\n}\n",
        )
        myFixture.addFileToProject(
            "Svc.php",
            "<?php\nnamespace App;\nfinal class Svc extends Base implements Marker {\n" +
                " public function run(int \$a, ?string \$b = null): bool { return true; }\n}\n",
        )
    }

    fun testPhpImportedClassesAreOutlined() = myFixture.testInlineCompletion(timeout = 60.seconds) {
        withWriteAction { addPhpDeps() }
        init(PhpFileType.INSTANCE, "<?php\nnamespace Other;\nuse App\\Svc;\nclass Foo { function bar() { \$this-><caret> } }")
        callInlineCompletion()
        delay()
        val imported = fake.contexts.single().importedClasses.associate { it.path to it.content }
        val svc = imported["App\\Svc"] ?: error(imported.keys.toString())
        assertTrue(svc, svc.contains("namespace App;"))
        assertTrue(svc, svc.contains("final class Svc extends Base implements Marker"))
        assertTrue(svc, svc.contains("public function run(int \$a"))
    }

    fun testPhpParentClassOutlineSkipsPrivateMembers() = myFixture.testInlineCompletion(timeout = 60.seconds) {
        withWriteAction { addPhpDeps() }
        init(PhpFileType.INSTANCE, "<?php\nnamespace Other;\nclass Foo extends \\App\\Base { function bar() { \$this-><caret> } }")
        callInlineCompletion()
        delay()
        val base = fake.contexts.single().importedClasses.single { it.path == "App\\Base" }.content
        assertTrue(base, base.contains("abstract class Base"))
        assertTrue(base, base.contains("const X = 1;"))
        assertTrue(base, base.contains("protected int \$count"))
        assertTrue(base, base.contains("public static function make()"))
        assertFalse(base, base.contains("secret"))
        assertFalse(base, base.contains("hidden"))
    }

    fun testImportedClassesCanBeDisabled() = myFixture.testInlineCompletion(timeout = 60.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.includeImportedClasses = false
        withWriteAction { addPhpDeps() }
        init(PhpFileType.INSTANCE, "<?php\nnamespace Other;\nuse App\\Svc;\nclass Foo { function bar() { \$this-><caret> } }")
        callInlineCompletion()
        delay()
        assertTrue(fake.contexts.single().importedClasses.isEmpty())
    }

    fun testYamlDirectCallAfterKey() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "services:\n  app.mailer:\n    class: <caret>\n")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
    }

    fun testYamlTypingAtEndOfLine() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "services:\n  app.mailer:\n    class: App\\Mai<caret>\n")
        typeChar('l')
        delay()
        assertInlineElements { gray("world") }
    }

    fun testYamlBlankIndentedLine() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "services:\n  app.mailer:\n    <caret>\n")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
    }

    fun testYamlListItem() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "items:\n  - <caret>\n")
        callInlineCompletion()
        delay()
        assertInlineElements { gray("world") }
    }

    fun testYamlTypingNewlineAfterKey() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "services:<caret>")
        typeChar('\n')
        delay()
        assertInlineElements { gray("world") }
    }

    fun testYamlTypingWithLookupOpen() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "a: foobar1\nb: foobar2\nkey: foo<caret>\n")
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait { myFixture.completeBasic() }
        typeChar('b')
        delay()
        assertInlineElements { gray("world") }
    }

    fun testYamlSuggestionSurvivesLookupPopup() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(YAMLFileType.YML, "a: foobar1\nb: foobar2\nkey: foo<caret>\n")
        typeChar('b')
        delay()
        assertInlineElements { gray("world") }
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait { myFixture.completeBasic() }
        delay()
        assertInlineElements { gray("world") }
    }

    fun testProviderSettingRoutesToSelectedProvider() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        ClaudeAutocompleteSettings.getInstance().state.provider = "codex"
        init(PlainTextFileType.INSTANCE, "route19 <caret>")
        callInlineCompletion()
        delay()
        assertEquals(ProviderKind.Codex, fake.configs.single().provider)
    }

    fun testCacheIsSeparatePerProvider() = myFixture.testInlineCompletion(timeout = 30.seconds) {
        init(PlainTextFileType.INSTANCE, "cache20 <caret>")
        callInlineCompletion()
        delay()
        escape()
        ClaudeAutocompleteSettings.getInstance().state.provider = "codex"
        callInlineCompletion()
        delay()
        assertEquals(listOf(ProviderKind.Claude, ProviderKind.Codex), fake.configs.map { it.provider })
    }
}
