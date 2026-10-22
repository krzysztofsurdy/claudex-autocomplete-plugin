package dev.ksurdy.claudeautocomplete.completion

enum class Gate {
    Viewer,
    ReadOnly,
    OneLine,
    Embedded,
    InjectedFragment,
    UnsupportedKind,
    UndoRedo,
    BulkUpdate,
    Selection,
    MultiCaret,
    LiveTemplate,
    LookupOpen,
    NextEditShown,
    Duplicate,
}

data class RequestStamp(val modificationStamp: Long, val caretOffset: Int)

data class EditorSnapshot(
    val stamp: RequestStamp,
    val viewer: Boolean = false,
    val writable: Boolean = true,
    val oneLine: Boolean = false,
    val embedded: Boolean = false,
    val injectedFragment: Boolean = false,
    val supportedKind: Boolean = true,
    val undoRedoInProgress: Boolean = false,
    val bulkUpdate: Boolean = false,
    val hasSelection: Boolean = false,
    val caretCount: Int = 1,
    val liveTemplateActive: Boolean = false,
    val liveTemplateCommand: Boolean = false,
    val lookupOpen: Boolean = false,
    val nextEditShown: Boolean = false,
)

object CompletionGates {
    fun blockedBy(snapshot: EditorSnapshot, manual: Boolean, showWhileLookupOpen: Boolean, last: RequestStamp?): Gate? {
        editorGate(snapshot)?.let { return it }
        if (manual) return null
        return stateGate(snapshot, showWhileLookupOpen) ?: Gate.Duplicate.takeIf { snapshot.stamp == last }
    }

    private fun editorGate(snapshot: EditorSnapshot): Gate? = when {
        snapshot.viewer -> Gate.Viewer
        !snapshot.writable -> Gate.ReadOnly
        snapshot.oneLine -> Gate.OneLine
        snapshot.embedded -> Gate.Embedded
        snapshot.injectedFragment -> Gate.InjectedFragment
        !snapshot.supportedKind -> Gate.UnsupportedKind
        snapshot.caretCount > 1 -> Gate.MultiCaret
        else -> null
    }

    private fun stateGate(snapshot: EditorSnapshot, showWhileLookupOpen: Boolean): Gate? = when {
        snapshot.undoRedoInProgress -> Gate.UndoRedo
        snapshot.bulkUpdate -> Gate.BulkUpdate
        snapshot.hasSelection -> Gate.Selection
        snapshot.liveTemplateActive || snapshot.liveTemplateCommand -> Gate.LiveTemplate
        snapshot.lookupOpen && !showWhileLookupOpen -> Gate.LookupOpen
        snapshot.nextEditShown -> Gate.NextEditShown
        else -> null
    }
}
