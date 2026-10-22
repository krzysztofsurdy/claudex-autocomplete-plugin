package dev.ksurdy.claudeautocomplete.nextedit

sealed interface TabOutcome {
    data object None : TabOutcome
    data class Jump(val offset: Int) : TabOutcome
    data class Apply(val edit: NextEdit) : TabOutcome
}

class NextEditState {
    enum class Phase { Hidden, Shown, Jumped }

    var edit: NextEdit? = null
        private set

    var phase: Phase = Phase.Hidden
        private set

    val isVisible: Boolean get() = phase != Phase.Hidden

    fun show(next: NextEdit) {
        edit = next
        phase = Phase.Shown
    }

    fun dismiss() {
        edit = null
        phase = Phase.Hidden
    }

    fun onTab(): TabOutcome {
        val current = edit ?: return TabOutcome.None
        return when (phase) {
            Phase.Hidden -> TabOutcome.None
            Phase.Shown -> {
                phase = Phase.Jumped
                TabOutcome.Jump(current.startOffset)
            }
            Phase.Jumped -> {
                dismiss()
                TabOutcome.Apply(current)
            }
        }
    }
}
