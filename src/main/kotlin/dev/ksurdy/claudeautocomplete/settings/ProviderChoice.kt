package dev.ksurdy.claudeautocomplete.settings

enum class ProviderChoice(val stored: String, val label: String) {
    Claude("claude", "Claude Code"),
    Codex("codex", "Codex"),
    ;

    val showsClaudeGroup: Boolean get() = this == Claude
    val showsCodexGroup: Boolean get() = this == Codex

    companion object {
        fun fromStored(value: String?): ProviderChoice = if (value == Codex.stored) Codex else Claude

        fun fromLabel(label: String?): ProviderChoice = if (label == Codex.label) Codex else Claude
    }
}
