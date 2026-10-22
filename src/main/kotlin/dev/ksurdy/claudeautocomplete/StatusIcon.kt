package dev.ksurdy.claudeautocomplete

enum class StatusIconState { Off, Ready, Thinking }

object StatusIcon {
    const val BLINK_MS = 500

    fun state(enabled: Boolean, consentGiven: Boolean, status: ClaudeStatus): StatusIconState = when {
        !enabled || !consentGiven -> StatusIconState.Off
        status is ClaudeStatus.Error || status is ClaudeStatus.LimitReached -> StatusIconState.Off
        status is ClaudeStatus.Thinking -> StatusIconState.Thinking
        else -> StatusIconState.Ready
    }

    fun isBlinking(state: StatusIconState): Boolean = state == StatusIconState.Thinking

    fun isColoured(state: StatusIconState, phase: Int): Boolean = when (state) {
        StatusIconState.Off -> false
        StatusIconState.Ready -> true
        StatusIconState.Thinking -> phase % 2 == 0
    }
}
