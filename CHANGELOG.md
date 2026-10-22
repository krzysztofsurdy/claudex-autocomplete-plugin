# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Alternative suggestions: cycle with the platform's Next/Previous suggestion actions (Alt+] / Alt+[). The model is asked for another suggestion only when you press Next on the newest one (up to 3), and is told what was already shown.
- Mid-line suggestions with code right of the caret, but not inside a word.
- Multi-line suggestions stream in while the model writes: each finished, bracket-balanced line shows up at once.
- Next edit suggestions (opt-in, off by default): after an edit and a pause, predicts your next edit on another line of the same file, highlights it and shows the replacement. Tab jumps to it, Tab again applies it, Esc or any typing dismisses it.
- Setting "Show suggestions while the completion popup is open" (off by default).

### Changed

- Open tabs are no longer sent whole. The prompt now carries up to 4 snippets of about 60 lines, taken from open files (same language first) and ranked by how many identifiers they share with the code around the caret. Each snippet shows its path and line range, the most relevant one comes last, and the "Include related snippets from open tabs" budget applies to all snippets together. Unrelated tabs are no longer sent at all.
- Typing no longer starts a request in the wrong editor state: with a selection, with several carets, during undo, redo or a bulk update, while a live template is active or was just expanded, while the completion popup is open (see the new setting), and while a next edit suggestion is shown. Viewers, one-line fields, dialog and popup editors, consoles, diff panes and injected-language fragment editors get no suggestions at all. The manual triggers still work, except in those editor kinds and with several carets.
- Next edit suggestions adapt to you: the pause before asking grows by half after each rejection and shrinks by a fifth after each acceptance (50 ms to 3 s, kept for the IDE session), an extra 2 s is waited with the caret at the end of a line you are still typing, and nothing is asked for 5 s after a rejection. A rejected suggestion is not shown again in that file for 5 minutes, and a rejected line gets no other suggestion for 30 s.
- Next edit suggestions are also requested when you move the caret to another line shortly after an edit, and always wait while an inline completion is shown or running.
- Next edit history keeps 20 edits and sends the newest ones within about 1000 tokens as compact numbered diff lines.
- The Claude Code process starts when a project opens (only after consent and with "Keep CLI process alive"), so the first suggestion does not wait for the CLI to boot. No prompt is sent.
- The status bar widget is an icon without text: grey when completions are off, after an error or at a usage limit, coloured when ready, blinking while a request runs. State, latency, model and 5h / 7d usage are in the tooltip. The "Show request state and usage in status bar" setting is gone.
- Default max completion lines raised from 12 to 30, so whole method bodies fit.

### Fixed

- With the caret at the end of an open signature such as `public function archiveAction(`, the suggestion stopped at `): Response {`. It now writes the whole method: the rest of the parameters, the return type, the real body and the closing brace. A line ending in `(` now gets a multi-line suggestion, a new `declaration` mode asks for the whole method, and the closing `}` of a completed method is no longer trimmed away.
- The suggestion hint showed the internal provider id ("Tab to complete dev.ksurdy.claudeautocomplete.ClaudeInlineCompletionProvider"). It now shows the Claudex icon, the name and the active provider and model, for example "Claudex · Claude haiku".
- Suggestions could be lazy placeholders, for example a "not implemented" exception body written straight after `archiveAction(`. The prompt now forbids placeholder code, asks for a real implementation based on the signature and similar code, and completes the construct at the cursor (such as the parameter list) first.
- The model's explanations were sometimes inserted as code ("The cursor sits inside ...", "Wait, that's not right"). The model now wraps the insertion in `<completion>` tags and everything outside is ignored. Replies without tags go through a prose guard, and streaming shows only the text inside the tags.
- In an empty function or method body the suggestion is the complete body, not just the first statement.
- Over-long multi-line suggestions are cut at a balanced line instead of inside an open block.
- A reply that degenerates into a loop (the same token or short pattern repeated over and over at the end) is cut back to before the loop, or dropped when nothing sensible remains.
- A suggestion that only repeats the next lines already in the file below the caret is dropped, or loses those last lines.
- Closing lines such as `}`, `)`, `});` or `end` that already exist below the caret, also after blank lines or with other indentation, are no longer suggested a second time, and `} else {` no longer doubles the existing brace. These cuts never change text already streamed in.
- Continuation lines returned without indentation are shifted to the caret indent.
- A blank line between two methods sometimes got no suggestion because the model answered with an empty completion. The prompt now asks for the most likely next member or statement there.
- Alternative suggestions could repeat a suggestion that was already shown. A reply that matches a shown one (ignoring whitespace and a trailing `;`, `,` or `.`) is asked for once more, and if it is still a duplicate nothing new is added.

## [1.0.0] - 2026-10-06

### Added

- Codex CLI as a second completion provider, selectable next to Claude Code in settings.
- Braille spinner with elapsed time while generating.
- Status bar widget with request state and usage windows, refreshed after completions, on demand and via Test Connection.
- File context: whole file or lines around the caret.
- Context from open editor tabs.
- Outlines of imported classes as context for PHP files.
- Custom prompt additions in settings.
- First-run consent notification. Completions stay off until you enable them, nothing is sent before.
- Tools | Trigger Claudex Autocomplete forces a completion, also mid-line. Assign a shortcut in Keymap.
- Plugin settings are included in the Settings search.

### Changed

- Renamed to Claudex Autocomplete with a new logo.
- Claude Code runs as a persistent process for lower latency.
- Usage windows are labelled from their actual duration, including weekly limits for Codex.
- Settings, notification and status bar texts follow JetBrains UI guidelines.
- CLI no longer starts at IDE startup, only on first use after consent.

### Fixed

- Declining the consent prompt now stays declined.
- Plugin Verifier compatibility: internal PHP API usage removed.

### Security

- Files that look like secrets (such as `.env`, keys and credentials) are never sent as context.
- Files ignored by VCS are never sent as context.

## [0.1.0] - 2026-09-18

### Added

- Initial release: inline completions through the Claude Code CLI.

[Unreleased]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/releases/tag/v1.0.0
[0.1.0]: https://plugins.jetbrains.com/plugin/34817-claudex-autocomplete
