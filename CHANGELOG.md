# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
