package dev.ksurdy.claudeautocomplete.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompletionGatesTest {
    private val plain = EditorSnapshot()

    private fun automatic(snapshot: EditorSnapshot, showWhileLookupOpen: Boolean = false) =
        CompletionGates.blockedBy(snapshot, manual = false, showWhileLookupOpen = showWhileLookupOpen)

    private fun manual(snapshot: EditorSnapshot, showWhileLookupOpen: Boolean = false) =
        CompletionGates.blockedBy(snapshot, manual = true, showWhileLookupOpen = showWhileLookupOpen)

    @Test
    fun `a plain editor passes both ways`() {
        assertNull(automatic(plain))
        assertNull(manual(plain))
    }

    @Test
    fun `viewers are blocked even for manual requests`() {
        val snapshot = plain.copy(viewer = true)
        assertEquals(Gate.Viewer, automatic(snapshot))
        assertEquals(Gate.Viewer, manual(snapshot))
    }

    @Test
    fun `read only documents are blocked even for manual requests`() {
        val snapshot = plain.copy(writable = false)
        assertEquals(Gate.ReadOnly, automatic(snapshot))
        assertEquals(Gate.ReadOnly, manual(snapshot))
    }

    @Test
    fun `one line editors are blocked even for manual requests`() {
        val snapshot = plain.copy(oneLine = true)
        assertEquals(Gate.OneLine, automatic(snapshot))
        assertEquals(Gate.OneLine, manual(snapshot))
    }

    @Test
    fun `editors in dialogs or popups are blocked even for manual requests`() {
        val snapshot = plain.copy(embedded = true)
        assertEquals(Gate.Embedded, automatic(snapshot))
        assertEquals(Gate.Embedded, manual(snapshot))
    }

    @Test
    fun `injected fragment editors are blocked even for manual requests`() {
        val snapshot = plain.copy(injectedFragment = true)
        assertEquals(Gate.InjectedFragment, automatic(snapshot))
        assertEquals(Gate.InjectedFragment, manual(snapshot))
    }

    @Test
    fun `console diff and preview editors are blocked even for manual requests`() {
        val snapshot = plain.copy(supportedKind = false)
        assertEquals(Gate.UnsupportedKind, automatic(snapshot))
        assertEquals(Gate.UnsupportedKind, manual(snapshot))
    }

    @Test
    fun `undo and redo block automatic requests only`() {
        val snapshot = plain.copy(undoRedoInProgress = true)
        assertEquals(Gate.UndoRedo, automatic(snapshot))
        assertNull(manual(snapshot))
    }

    @Test
    fun `bulk updates block automatic requests only`() {
        val snapshot = plain.copy(bulkUpdate = true)
        assertEquals(Gate.BulkUpdate, automatic(snapshot))
        assertNull(manual(snapshot))
    }

    @Test
    fun `a selection blocks automatic requests only`() {
        val snapshot = plain.copy(hasSelection = true)
        assertEquals(Gate.Selection, automatic(snapshot))
        assertNull(manual(snapshot))
    }

    @Test
    fun `several carets block every request`() {
        val snapshot = plain.copy(caretCount = 2)
        assertEquals(Gate.MultiCaret, automatic(snapshot))
        assertEquals(Gate.MultiCaret, manual(snapshot))
    }

    @Test
    fun `an active live template blocks automatic requests only`() {
        val snapshot = plain.copy(liveTemplateActive = true)
        assertEquals(Gate.LiveTemplate, automatic(snapshot))
        assertNull(manual(snapshot))
    }

    @Test
    fun `a live template expansion command blocks automatic requests only`() {
        val snapshot = plain.copy(liveTemplateCommand = true)
        assertEquals(Gate.LiveTemplate, automatic(snapshot))
        assertNull(manual(snapshot))
    }

    @Test
    fun `an open completion popup blocks automatic requests by default`() {
        val snapshot = plain.copy(lookupOpen = true)
        assertEquals(Gate.LookupOpen, automatic(snapshot))
        assertNull(manual(snapshot))
    }

    @Test
    fun `an open completion popup is allowed when the setting is on`() {
        val snapshot = plain.copy(lookupOpen = true)
        assertNull(automatic(snapshot, showWhileLookupOpen = true))
    }

    @Test
    fun `a shown next edit blocks automatic requests only`() {
        val snapshot = plain.copy(nextEditShown = true)
        assertEquals(Gate.NextEditShown, automatic(snapshot))
        assertNull(manual(snapshot))
    }

    @Test
    fun `editor kind gates win over state gates`() {
        val snapshot = plain.copy(viewer = true, hasSelection = true, lookupOpen = true)
        assertEquals(Gate.Viewer, automatic(snapshot))
    }
}
