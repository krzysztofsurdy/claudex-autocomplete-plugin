package dev.ksurdy.claudeautocomplete.nextedit

class NextEditGate(
    private val timing: NextEditTiming,
    private val memory: RejectionMemory,
    private val caretTrigger: CaretTrigger,
    private val fileKey: () -> String,
) {
    fun edited() = caretTrigger.recordEdit()

    fun caretMoved(fromLine: Int, toLine: Int): Boolean = caretTrigger.shouldRequest(fromLine, toLine)

    fun pauseMs(baseMs: Long): Long = timing.pauseMs(baseMs)

    fun extraWaitMs(lineText: String, caretColumn: Int): Long = timing.extraWaitMs(lineText, caretColumn)

    fun beforeRequest(): SkipReason? = timing.requestBlock()

    fun beforeShow(edit: NextEdit): SkipReason? = when {
        memory.contains(fileKey(), edit.startLine, edit.endLine, edit.newText) -> SkipReason.Remembered
        else -> timing.lineBlock(edit.startLine)
    }

    fun rejected(edit: NextEdit, baseMs: Long) {
        timing.recordRejection(edit.startLine, baseMs)
        memory.remember(fileKey(), edit.startLine, edit.endLine, edit.newText)
    }

    fun accepted(baseMs: Long) = timing.recordAcceptance(baseMs)
}
