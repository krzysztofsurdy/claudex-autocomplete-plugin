package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import dev.ksurdy.claudeautocomplete.BackendService
import dev.ksurdy.claudeautocomplete.ClaudeAutocompleteSettings
import dev.ksurdy.claudeautocomplete.ClaudexBundle
import dev.ksurdy.claudeautocomplete.SensitiveFiles
import dev.ksurdy.claudeautocomplete.StatusService
import dev.ksurdy.claudeautocomplete.FailureReporter
import dev.ksurdy.claudeautocomplete.backend.CompletionResult
import dev.ksurdy.claudeautocomplete.isNextEditActive
import dev.ksurdy.claudeautocomplete.toBackendConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class NextEditController private constructor(private val editor: Editor) : Disposable {
    val state = NextEditState()
    val history = EditHistory()
    val presenter = NextEditPresenter(editor)

    private val document = editor.document
    private val gate = NextEditService.getInstance().newGate { FileDocumentManager.getInstance().getFile(document)?.path.orEmpty() }
    private var pending: Job? = null
    private var applying = false
    private var jumping = false
    private var changeStartLine = 0
    private var changeBefore: String? = null

    private val documentListener = object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) = captureBefore(event)

        override fun documentChanged(event: DocumentEvent) = onDocumentChanged(event)
    }

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            if (jumping || applying) return
            if (state.isVisible) dismiss(CARET_MOVED)
            if (gate.caretMoved(event.oldPosition.line, event.newPosition.line)) schedule()
        }
    }

    private fun begin() {
        document.addDocumentListener(documentListener, this)
        editor.caretModel.addCaretListener(caretListener, this)
    }

    fun canHandleTab(): Boolean = canHandleKey()

    fun canHandleEscape(): Boolean = canHandleKey()

    private fun canHandleKey(): Boolean =
        state.isVisible && !editor.isDisposed && editor.caretModel.caretCount == 1 && !ghostTextVisible(editor)

    fun onTab() {
        when (val outcome = state.onTab()) {
            is TabOutcome.Jump -> {
                state.edit?.let { edit -> LOG.debug { NextEditLog.jumped(edit) } }
                jump(outcome.offset)
            }
            is TabOutcome.Apply -> apply(outcome.edit)
            TabOutcome.None -> Unit
        }
    }

    fun onEscape() = dismiss(ESCAPE)

    fun dismiss(reason: String) {
        val shown = state.edit
        if (state.isVisible) LOG.debug { NextEditLog.dismissed(reason) }
        if (shown != null && reason in REJECTIONS) gate.rejected(shown, baseMs())
        state.dismiss()
        presenter.clear()
    }

    private fun jump(offset: Int) {
        jumping = true
        try {
            editor.caretModel.moveToOffset(offset)
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        } finally {
            jumping = false
        }
        presenter.showApplyHint()
    }

    private fun apply(edit: NextEdit) {
        presenter.clear()
        val project = editor.project
        if (project == null || edit.endOffset > document.textLength || !EditorModificationUtil.requestWriting(editor)) return
        applying = true
        gate.accepted(baseMs())
        LOG.debug { NextEditLog.applied(edit) }
        try {
            WriteCommandAction.runWriteCommandAction(project, ClaudexBundle.message("nextedit.command.name"), null, Runnable {
                document.replaceString(edit.startOffset, edit.endOffset, edit.newText)
                editor.caretModel.moveToOffset(edit.startOffset + edit.newText.length)
            })
        } finally {
            applying = false
        }
    }

    private fun captureBefore(event: DocumentEvent) {
        val doc = event.document
        val first = doc.getLineNumber(event.offset)
        val last = doc.getLineNumber(minOf(event.offset + event.oldLength, doc.textLength))
        val range = TextRange(doc.getLineStartOffset(first), doc.getLineEndOffset(last))
        changeStartLine = first
        changeBefore = if (range.length <= MAX_CAPTURE_CHARS && isTracking()) doc.getText(range) else null
    }

    private fun onDocumentChanged(event: DocumentEvent) {
        dismiss(DOCUMENT_CHANGED)
        pending?.cancel()
        val before = changeBefore
        changeBefore = null
        if (event.isWholeTextReplaced) history.clear()
        if (before != null && !event.isWholeTextReplaced) {
            val doc = event.document
            val last = doc.getLineNumber(minOf(event.offset + event.newLength, doc.textLength))
            val after = doc.getText(TextRange(doc.getLineStartOffset(changeStartLine), doc.getLineEndOffset(last)))
            history.record(changeStartLine + 1, before, after)
            gate.edited()
        }
        if (!applying && before != null && !event.isWholeTextReplaced) schedule()
    }

    private fun isTracking(): Boolean {
        val settings = ClaudeAutocompleteSettings.getInstance().state
        if (!settings.isNextEditActive) return false
        val file = FileDocumentManager.getInstance().getFile(document) ?: return false
        return !SensitiveFiles.isSensitive(file.name, settings.excludedFilePatterns.orEmpty())
    }

    private fun baseMs(): Long = ClaudeAutocompleteSettings.getInstance().state.debounceMs.coerceAtLeast(0) * PAUSE_FACTOR.toLong()

    private fun schedule() {
        val service = NextEditService.getInstance()
        pending?.cancel()
        pending = service.scope.launch {
            service.sleep(gate.pauseMs(baseMs()))
            gate.beforeRequest()?.let { reason ->
                LOG.debug { NextEditLog.skipped(reason) }
                return@launch
            }
            waitAtLineEnd(service)
            if (awaitInlineIdle()) requestNextEdit()
        }
    }

    private suspend fun waitAtLineEnd(service: NextEditService) {
        val extra = withContext(Dispatchers.EDT) {
            if (editor.isDisposed) return@withContext 0L
            val offset = editor.caretModel.offset
            val line = document.getLineNumber(offset)
            val start = document.getLineStartOffset(line)
            gate.extraWaitMs(document.getText(TextRange(start, document.getLineEndOffset(line))), offset - start)
        }
        if (extra <= 0) return
        LOG.debug { NextEditLog.waiting("end of line", extra) }
        service.sleep(extra)
    }

    private suspend fun awaitInlineIdle(): Boolean {
        val idle = InlineWait.await(
            MAX_INLINE_WAITS,
            isBusy = { withContext(Dispatchers.EDT) { !editor.isDisposed && inlineBusy(editor) } },
            onWait = { LOG.debug { NextEditLog.waiting(SkipReason.InlineCompletion.label, INLINE_RECHECK_MS) } },
            sleep = { delay(INLINE_RECHECK_MS) },
        )
        if (!idle) LOG.debug { NextEditLog.skipped(SkipReason.InlineCompletion) }
        return idle
    }

    private suspend fun requestNextEdit() {
        val settings = ClaudeAutocompleteSettings.getInstance().state
        if (!settings.isNextEditActive || StatusService.getInstance().isBlocked(Instant.now())) return
        val request = readAction { NextEditRequestFactory.create(editor, history, settings) } ?: return
        val config = ClaudeAutocompleteSettings.getInstance().toBackendConfig()
        val startedAt = System.currentTimeMillis()
        LOG.debug { NextEditLog.requested(request.context.languageId, request.context.nextEdit?.edits?.size ?: 0) }
        val result = try {
            withContext(Dispatchers.IO) { BackendService.getInstance().engine.complete(request.context, config) }
        } catch (e: CancellationException) {
            LOG.debug { NextEditLog.cancelled(System.currentTimeMillis() - startedAt) }
            throw e
        }
        val ms = System.currentTimeMillis() - startedAt
        when (result) {
            is CompletionResult.Success -> present(result.text, request, ms)
            CompletionResult.Empty -> LOG.debug { NextEditLog.empty(ms) }
            is CompletionResult.Failure -> {
                LOG.debug { NextEditLog.failed(result.kind.name, ms) }
                StatusService.getInstance().update(FailureReporter.report(result, config.provider))
            }
        }
    }

    private suspend fun present(raw: String, request: NextEditRequest, ms: Long) = withContext(Dispatchers.EDT) {
        val parsed = NextEditParser.parse(raw)
        LOG.debug { NextEditLog.received(ms, parsed) }
        staleReason(request)?.let { reason ->
            LOG.debug { NextEditLog.dropped(reason) }
            return@withContext
        }
        val caretLine = document.getLineNumber(request.caretOffset) + 1
        when (val outcome = NextEditTarget.evaluate(parsed, document.charsSequence, caretLine)) {
            is TargetOutcome.Dropped -> LOG.debug { NextEditLog.dropped(NextEditLog.reason(outcome.reason, raw)) }
            is TargetOutcome.Chosen -> {
                gate.beforeShow(outcome.edit)?.let { reason ->
                    LOG.debug { NextEditLog.skipped(reason) }
                    return@withContext
                }
                state.show(outcome.edit)
                presenter.show(outcome.edit)
                LOG.debug { NextEditLog.shown(outcome.edit) }
            }
        }
    }

    private fun staleReason(request: NextEditRequest): String? = when {
        editor.isDisposed -> "editor disposed"
        document.modificationStamp != request.modificationStamp -> "stale document"
        editor.caretModel.offset != request.caretOffset -> "stale caret"
        ghostTextVisible(editor) -> "ghost text visible"
        else -> null
    }

    override fun dispose() {
        pending?.cancel()
        presenter.clear()
        state.dismiss()
    }

    companion object {
        private val LOG = Logger.getInstance(NextEditController::class.java)
        private val KEY = Key.create<NextEditController>("claude.autocomplete.next.edit")
        private const val PAUSE_FACTOR = 2
        private const val ESCAPE = "escape"
        private const val DOCUMENT_CHANGED = "document changed"
        private const val CARET_MOVED = "caret moved"
        private val REJECTIONS = setOf(ESCAPE, DOCUMENT_CHANGED)
        private const val MAX_INLINE_WAITS = 50
        private const val INLINE_RECHECK_MS = 100L
        private const val MAX_CAPTURE_CHARS = 20_000

        fun of(editor: Editor): NextEditController? = editor.getUserData(KEY)

        fun attach(editor: Editor) {
            if (of(editor) != null) return
            val controller = NextEditController(editor)
            editor.putUserData(KEY, controller)
            controller.begin()
        }

        fun detach(editor: Editor) {
            val controller = of(editor) ?: return
            editor.putUserData(KEY, null)
            Disposer.dispose(controller)
        }

        fun ghostTextVisible(editor: Editor): Boolean = InlineCompletionContext.getOrNull(editor)?.isCurrentlyDisplaying() == true

        fun inlineBusy(editor: Editor): Boolean = ghostTextVisible(editor) || InlineCompletionSession.getOrNull(editor)?.isActive() == true
    }
}
