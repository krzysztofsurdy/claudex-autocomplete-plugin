package dev.ksurdy.claudeautocomplete.nextedit

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
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
            if (!jumping && !applying && state.isVisible) dismiss()
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
            is TabOutcome.Jump -> jump(outcome.offset)
            is TabOutcome.Apply -> apply(outcome.edit)
            TabOutcome.None -> Unit
        }
    }

    fun onEscape() = dismiss()

    fun dismiss() {
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
        dismiss()
        pending?.cancel()
        val before = changeBefore
        changeBefore = null
        if (event.isWholeTextReplaced) history.clear()
        if (before != null && !event.isWholeTextReplaced) {
            val doc = event.document
            val last = doc.getLineNumber(minOf(event.offset + event.newLength, doc.textLength))
            val after = doc.getText(TextRange(doc.getLineStartOffset(changeStartLine), doc.getLineEndOffset(last)))
            history.record(changeStartLine + 1, before, after)
        }
        if (!applying && before != null && !event.isWholeTextReplaced) schedule()
    }

    private fun isTracking(): Boolean {
        val settings = ClaudeAutocompleteSettings.getInstance().state
        if (!settings.isNextEditActive) return false
        val file = FileDocumentManager.getInstance().getFile(document) ?: return false
        return !SensitiveFiles.isSensitive(file.name, settings.excludedFilePatterns.orEmpty())
    }

    private fun schedule() {
        val pause = ClaudeAutocompleteSettings.getInstance().state.debounceMs.coerceAtLeast(0) * PAUSE_FACTOR
        pending = NextEditService.getInstance().scope.launch {
            delay(pause.toLong())
            requestNextEdit()
        }
    }

    private suspend fun requestNextEdit() {
        val settings = ClaudeAutocompleteSettings.getInstance().state
        if (!settings.isNextEditActive || StatusService.getInstance().isBlocked(Instant.now())) return
        if (withContext(Dispatchers.EDT) { ghostTextVisible(editor) }) return
        val request = readAction { NextEditRequestFactory.create(editor, history, settings) } ?: return
        val config = ClaudeAutocompleteSettings.getInstance().toBackendConfig()
        when (val result = withContext(Dispatchers.IO) { BackendService.getInstance().engine.complete(request.context, config) }) {
            is CompletionResult.Success -> present(result.text, request)
            CompletionResult.Empty -> Unit
            is CompletionResult.Failure -> StatusService.getInstance().update(FailureReporter.report(result, config.provider))
        }
    }

    private suspend fun present(raw: String, request: NextEditRequest) = withContext(Dispatchers.EDT) {
        if (editor.isDisposed || document.modificationStamp != request.modificationStamp) return@withContext
        if (editor.caretModel.offset != request.caretOffset || ghostTextVisible(editor)) return@withContext
        val caretLine = document.getLineNumber(request.caretOffset) + 1
        val edit = NextEditTarget.choose(NextEditParser.parse(raw), document.charsSequence, caretLine) ?: return@withContext
        state.show(edit)
        presenter.show(edit)
    }

    override fun dispose() {
        pending?.cancel()
        presenter.clear()
        state.dismiss()
    }

    companion object {
        private val KEY = Key.create<NextEditController>("claude.autocomplete.next.edit")
        private const val PAUSE_FACTOR = 2
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
    }
}
