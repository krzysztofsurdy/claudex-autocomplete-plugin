package dev.ksurdy.claudeautocomplete.ide

import com.intellij.codeInsight.inline.completion.InlineCompletionLifecycleTestDSL
import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpFileType
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudeStatus
import dev.ksurdy.claudeautocomplete.CompletionEngine
import dev.ksurdy.claudeautocomplete.CompletionRequests
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.backend.CompletionContext
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration.Companion.seconds

class TypedDeclarationIdeTest : BasePlatformTestCase() {
    private lateinit var fake: FakeCompletionBackend
    private lateinit var original: CompletionEngine

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        val service = BackendService.getInstance()
        original = service.engine
        fake = FakeCompletionBackend(CompletionResult.Empty)
        fake.responder = ::answer
        service.engine = fake
        ClaudeAutocompleteSettings.getInstance().state.debounceMs = 0
        ClaudeAutocompleteSettings.getInstance().state.consentGiven = true
    }

    override fun tearDown() {
        try {
            CompletionRequests.getInstance().cancel()
            BackendService.getInstance().engine = original
            StatusService.getInstance().update(ClaudeStatus.Ready)
            ClaudeAutocompleteSettings.getInstance().loadState(ClaudeAutocompleteSettings.State())
        } finally {
            super.tearDown()
        }
    }

    private fun answer(context: CompletionContext): CompletionResult {
        val line = context.prefix.substringAfterLast('\n').trimStart()
        return when {
            context.declaration -> CompletionResult.Success(WHOLE_METHOD.removePrefix(line))
            context.multiline -> CompletionResult.Success("// multi-line")
            SIGNATURE.startsWith(line) && line.isNotEmpty() -> CompletionResult.Success(SIGNATURE.removePrefix(line).ifEmpty { "x" })
            else -> CompletionResult.Empty
        }
    }

    private suspend fun InlineCompletionLifecycleTestDSL.awaitUntil(description: String, condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(50)
        }
        assertTrue(description, condition())
    }

    fun testTypingAnOpenSignatureWithTheAutoClosedParenthesisAsksForTheWholeMethod() = myFixture.testInlineCompletion(timeout = 60.seconds) {
        init(PhpFileType.INSTANCE, classNamed("Auto"))
        SIGNATURE_HEAD.forEach { typeChar(it) }
        delay()
        val context = fake.contexts.last()
        assertTrue(context.declaration)
        assertTrue(context.multiline)
        assertEquals("\n}\n", context.suffix)
        insert()
        assertFileContent(EXPECTED_PHP)
    }

    fun testTheSingleLineSuggestionOfAnEarlierKeystrokeDoesNotAnswerTheOpenSignature() = myFixture.testInlineCompletion(timeout = 60.seconds) {
        init(PlainTextFileType.INSTANCE, classNamed("Earlier"))
        SIGNATURE_HEAD.dropLast(1).forEach { typeChar(it) }
        delay()
        assertInlineRender("(Request \$request): Response")
        typeChar('(')
        delay()
        assertTrue(fake.contexts.last().declaration)
        assertInlineRender(WHOLE_METHOD.substringAfter("("))
    }

    fun testTheCachedSingleLineSuggestionDoesNotAnswerTheOpenSignature() = myFixture.testInlineCompletion(timeout = 60.seconds) {
        init(PlainTextFileType.INSTANCE, classNamed("Dismissed"))
        SIGNATURE_HEAD.dropLast(1).forEach { typeChar(it) }
        delay()
        assertInlineRender("(Request \$request): Response")
        escape()
        typeChar('(')
        delay()
        assertTrue(fake.contexts.last().declaration)
        assertInlineRender(WHOLE_METHOD.substringAfter("("))
    }

    fun testTheRunningSingleLineRequestDoesNotAnswerTheOpenSignature() = myFixture.testInlineCompletion(timeout = 60.seconds) {
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        init(PlainTextFileType.INSTANCE, classNamed("Running"))
        SIGNATURE_HEAD.dropLast(1).forEach { typeChar(it) }
        awaitUntil("single line request") { fake.contexts.isNotEmpty() }
        typeChar('(')
        awaitUntil("declaration request") { fake.contexts.last().declaration }
        assertTrue(fake.contexts.first { !it.declaration }.multiline.not())
        gate.complete(Unit)
        delay()
        assertInlineRender(WHOLE_METHOD.substringAfter("("))
    }

    private fun classNamed(name: String) = "<?php\nclass $name\n{\n    <caret>\n}\n"

    private companion object {
        const val SIGNATURE_HEAD = "public function archiveAction("
        const val SIGNATURE = "public function archiveAction(Request \$request): Response"
        const val EXPECTED_PHP = "<?php\nclass Auto\n{\n    public function archiveAction(Request \$request): Response\n    {\n        return new Response();\n    }<caret>\n}\n"
        const val WHOLE_METHOD = "public function archiveAction(Request \$request): Response\n    {\n        return new Response();\n    }"
    }
}
