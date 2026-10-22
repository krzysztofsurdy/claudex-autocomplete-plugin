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

### Changed

- The Claude Code process starts when a project opens (only after consent and with "Keep CLI process alive"), so the first suggestion does not wait for the CLI to boot. No prompt is sent.
- Default max completion lines raised from 12 to 30, so whole method bodies fit.

### Fixed

- The model's explanations were sometimes inserted as code ("The cursor sits inside ...", "Wait, that's not right"). The model now wraps the insertion in `<completion>` tags and everything outside is ignored. Replies without tags go through a prose guard, and streaming shows only the text inside the tags.
- In an empty function or method body the suggestion is the complete body, not just the first statement.
- Over-long multi-line suggestions are cut at a balanced line instead of inside an open block.
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
