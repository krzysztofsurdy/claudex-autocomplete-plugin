# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.1] - 2026-10-24

### Changed

- New Marketplace description; the README has a Privacy section.

### Fixed

- After `$repo->` the inline suggestion now appears once the completion popup closes, instead of waiting for the next keystroke. All other gates still apply.
- Typing `public function name(` character by character now gets the whole method instead of one line. The `)` the IDE inserts after `(` no longer breaks declaration mode, and a single-line suggestion from an earlier keystroke is no longer reused there.

## [1.1.0] - 2026-10-22

### Added

- Whole body when you press Enter in an empty function or method, and the whole method (rest of the parameters, return type, body and closing brace) when a declaration line ends with `(`.
- Mid-line suggestions with code right of the caret, but not inside a word.
- Multi-line suggestions stream in while the model writes: each finished, bracket-balanced line shows up at once.
- Alternative suggestions with Alt+] / Alt+[ (the platform's Next and Previous suggestion actions). Another one is requested only when you press Next on the newest suggestion, up to 3, and the model is told what was already shown.
- A dismissed suggestion survives typing: typing its first characters shows the rest from the cache, without a new request.
- Typing what the model is writing keeps the running request, so its result still arrives without a restart.
- Prefetch after accept (on by default, setting "Prefetch the next suggestion after accept"): after you accept a whole suggestion, the next one is requested in the background so it is ready when you ask. It respects consent, the enabled switch, excluded files and the usage limit.
- Next edit suggestions (opt-in, off by default): after an edit and a pause, predicts your next edit on another line of the file and highlights it. Tab jumps to it, Tab again applies it, Esc or typing dismisses it.
- Setting "Show suggestions while the completion popup is open" (off by default).

### Changed

- Open tabs are no longer sent whole. The prompt carries up to 4 snippets of about 60 lines, from open files (same language first), ranked by the identifiers they share with the code around the caret. Each shows its path and line range, and the related snippets budget applies to all of them together.
- Typing no longer starts a request with a selection, with several carets, during undo, redo or a bulk update, while a live template is active, while the completion popup is open (see the new setting) or while a next edit suggestion is shown. Viewers, one-line fields, dialog and popup editors, consoles, diff panes and injected-language fragment editors get no suggestions. The manual triggers still work, except in those editor kinds and with several carets.
- Next edit suggestions adapt to you: the pause before asking grows after a rejection and shrinks after an acceptance, a rejected suggestion is not offered again for a while, and moving the caret to another line soon after an edit also triggers a request.
- The Claude Code process starts when a project opens, only after consent and with "Keep one CLI process alive" on, so the first suggestion does not wait for the CLI to boot. No prompt is sent.
- The status bar widget is an icon without text: grey when off, after an error or at a usage limit, coloured when ready, blinking while a request runs. State, latency, model and 5h / 7d usage are in the tooltip. The "Show request state and usage in status bar" setting is gone.
- Settings page regrouped by feature; only the selected provider's options are shown.
- Default max completion lines raised from 12 to 30, so whole method bodies fit.

### Fixed

- A suggestion at the end of an open signature such as `public function archiveAction(` stopped at `): Response {`. It now writes the whole method, and the closing `}` of a completed method is no longer trimmed away.
- Suggestions were sometimes lazy placeholders, such as a "not implemented" exception body. The prompt now forbids placeholder code and asks for a real implementation.
- The model's explanations were sometimes inserted as code. The insertion is now wrapped in `<completion>` tags, everything outside is ignored, and replies without tags go through a prose guard.
- In an empty function or method body the suggestion is the complete body, not just the first statement.
- Over-long multi-line suggestions are cut at a balanced line instead of inside an open block.
- A reply that ends in a repetition loop is cut back to before the loop, or dropped when nothing sensible remains.
- A suggestion that only repeats the next lines of the file is dropped or loses those lines.
- Closing lines such as `}`, `)`, `});` or `end` that already exist below the caret are no longer suggested a second time, and `} else {` no longer doubles the existing brace. These cuts never change text already streamed in.
- Continuation lines returned without indentation are shifted to the caret indent.
- A blank line between two methods sometimes got no suggestion because the model answered with an empty completion. The prompt now asks for the most likely next member or statement there.
- Alternative suggestions could repeat one that was already shown. A duplicate (ignoring whitespace and a trailing `;`, `,` or `.`) is asked for once more, and if it is still a duplicate nothing is added.
- The suggestion hint showed the internal provider id. It now shows the Claudex icon, the name and the active provider and model, for example "Claudex · Claude haiku".

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

[Unreleased]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/releases/tag/v1.1.1
[1.1.0]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/releases/tag/v1.1.0
[1.0.0]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/releases/tag/v1.0.0
[0.1.0]: https://plugins.jetbrains.com/plugin/34817-claudex-autocomplete
