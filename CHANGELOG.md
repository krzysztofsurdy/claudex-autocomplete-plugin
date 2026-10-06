# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-10-06

### Added

- Codex CLI as a second completion provider, selectable next to Claude Code in settings.
- Animated braille spinner with elapsed time while a suggestion is being generated.
- Status bar widget showing request state and subscription usage windows, refreshed on startup, on demand and every 10 minutes.
- Configurable file context: whole file or a number of lines around the caret.
- Context from open editor tabs.
- Outlines of imported classes as context for PHP files.
- Custom prompt additions in settings.
- First-run consent notification. Completions stay off, and nothing is sent to Anthropic or OpenAI, until you enable them.
- Tools | Trigger Claudex Autocomplete action to force a completion, including mid-line. Assign your own shortcut in Keymap.
- Plugin settings are included in the Settings search.

### Changed

- Renamed to Claudex Autocomplete with a new logo.
- Claude Code runs as a persistent process for lower latency.
- Usage windows are labelled from their actual duration, including weekly limits for Codex.
- Settings, notifications and status bar texts follow the JetBrains UI guidelines and live in a message bundle.
- The CLI is no longer started at IDE startup; it starts on first use after consent.

### Fixed

- Declining the consent prompt now stays declined.
- Inline completion works in YAML files.
- Compatibility with the JetBrains Plugin Verifier: internal PHP API usage removed.

### Security

- Files that look like secrets (such as `.env`, keys and credentials) are never sent as context.
- Files ignored by VCS are never sent as context.

## [0.1.0] - 2026-09-18

### Added

- Initial release: inline completions through the Claude Code CLI.

[Unreleased]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/releases/tag/v1.0.0
[0.1.0]: https://plugins.jetbrains.com/plugin/34817-claudex-autocomplete
