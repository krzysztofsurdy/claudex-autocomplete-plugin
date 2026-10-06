<p align="center"><img src="docs/logo.svg" width="160" alt="Claudex Autocomplete logo"></p>

<h1 align="center">Claudex Autocomplete</h1>

<p align="center">
  Inline ghost-text completions for PhpStorm and other JetBrains IDEs, powered by your local Claude Code CLI or Codex CLI.<br>
  It uses your existing Claude or ChatGPT subscription login. No API key needed.
</p>

<p align="center">
  <a href="https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/actions/workflows/ci.yml"><img src="https://github.com/krzysztofsurdy/claudex-autocomplete-plugin/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/krzysztofsurdy/claudex-autocomplete-plugin" alt="License: GPL-3.0"></a>
  <img src="https://img.shields.io/badge/JetBrains%20IDEs-2025.3%2B-000000?logo=jetbrains&logoColor=white" alt="JetBrains IDEs 2025.3+">
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.2">
</p>

## Features

- Gray inline suggestions after a short pause, in any language the IDE edits.
- Claude Code (`claude`) or Codex (`codex`), switch in settings or the status bar.
- Accept all, next word or rest of the line.
- Context: current file (whole or around the caret), open tabs, and for PHP outlines of imported classes, parent, interfaces and traits.
- Spinner with elapsed time while generating.
- Status bar widget with state, last latency and 5h / 7d usage.
- Custom prompt additions, e.g. "Follow PSR-12".

## Requirements

- JetBrains IDE 2025.3+ (build 253+).
- Claude Code CLI logged in with a Claude subscription, or Codex CLI logged in with ChatGPT.

## Installation

From [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/34817-claudex-autocomplete): Settings | Plugins | Marketplace, search "Claudex Autocomplete", Install.

From a local build: `./gradlew buildPlugin`, then Settings | Plugins | gear icon | Install Plugin from Disk... and pick `build/distributions/claudex-autocomplete-<version>.zip`.

## Quick start

1. Log in to a CLI:
   - Claude: run `claude`, then `/login`.
   - Codex: `npm i -g @openai/codex` (or `brew install codex`), then `codex login`.
2. Enable completions. They are off until you agree: pick Enable in the first-run notification, or tick "Enable completions" in Settings | Tools | Claudex Autocomplete. After Not now the prompt wont come back, enable later from Settings, the status bar or Tools | Toggle Claudex Autocomplete. Nothing goes to Anthropic or OpenAI before that.
3. Pick the Provider in settings and click Test Connection.
4. Type.

## Keyboard shortcuts

| Action | Shortcut |
|---|---|
| Accept whole suggestion | Tab |
| Accept next word | Alt+Right (macOS), Ctrl+Right (Windows/Linux) |
| Accept rest of line | Cmd+Right (macOS), End (Windows/Linux) |
| Trigger a completion manually | Shift+Alt+\ (Shift+Option+\ on macOS) |
| Dismiss | Esc |

Word and line accept reuse your keymap's Next Word and Line End. Tools | Toggle Claudex Autocomplete (or a click on the status bar widget) turns the plugin on and off.

Tools | Trigger Claudex Autocomplete forces a completion even mid-line. No default shortcut, assign one in Keymap.

## Settings

Settings | Tools | Claudex Autocomplete. Main options:

| Setting | Default | Notes |
|---|---|---|
| Enable completions | off | Set by the first-run notification or this checkbox |
| Provider | Claude Code | Claude Code or Codex |
| Claude Code CLI path / Codex CLI path | empty | Empty = auto-detect |
| Model (Claude) | haiku | haiku, sonnet, opus, fable or a full model id |
| Model (Codex) | gpt-5.3-codex | Any model your ChatGPT plan supports |
| Effort / Reasoning effort | low | Lower is faster |
| Debounce (ms) | 250 | Delay after typing before a request |
| Request timeout (ms) | 8000 | |
| Current file context | auto | Whole file up to 1000 lines, else 150 lines around the caret |
| Include open tabs | true | Character budget is configurable |
| Excluded file patterns | `.env`, `*.pem`, `id_rsa*`, ... | Comma-separated. Matching files, VCS-ignored files and files excluded from the project are never sent |
| Include imported classes | true | PHP only, signatures only |
| Multi-line mode | auto | auto / always / never |
| Keep CLI process alive | true | Claude Code only, Codex starts a process per request |
| Custom prompt additions | empty | Appended to the system prompt |
| Disabled languages | empty | Comma-separated language ids |

Test Connection runs one completion with the values in the form, without saving.

## How it works

After you pause, the plugin sends the CLI the code before and after the caret, the file window, optionally other open tabs and (PHP) imported class outlines. The reply shows as ghost text.

It only asks when the rest of the line is empty or closing characters, and never in read-only editors or files over 1,000,000 characters. `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` and `CLAUDE_CODE_*` are removed from the CLI environment, so your subscription login is used.

Your code context goes to Anthropic or OpenAI through the CLI, under your account's terms.

## Status bar

Shows `Claudex: Disabled` until you enable completions - click it and choose Enable Completions. Then provider and state, e.g. `Claude: Ready`, `Waiting…`, `Thinking… Ns`, last latency, `Error` or `Limit reached`. Only the usage window closest to its limit is shown, e.g. `Claude: Ready · 5h 41%` (` high` from 80%). The tooltip has both windows with reset times and the model. Click for Enable/Disable, Provider, Open settings and Refresh Usage.

No CLI starts at IDE startup. Usage refreshes after completions, on Refresh Usage and on Test Connection. At a usage limit, requests pause until the reset.

## Troubleshooting

- **Not logged in**: run `claude` then `/login`, or `codex login`.
- **CLI not found**: set the full path in settings, GUI-launched IDEs have a minimal PATH.
- **Slow**: use `haiku`, keep "Keep CLI process alive" on, effort `low`, thinking off, lower the open tabs budget or file window.
- **No suggestions**: check completions are enabled, the status bar, disabled languages, and that the caret is at the end of the code on its line.
- **Limit reached**: resumes after the reset time in the widget tooltip.

## Building from source

JDK 21. Gradle downloads PhpStorm 2025.3 as the target. To use a local PhpStorm 2025.3+ (offline, faster), set `localIdePath`; its bundled runtime works as the JDK.

```
export JAVA_HOME=/path/to/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew test         # unit tests and headless IDE platform tests
./gradlew buildPlugin  # zip in build/distributions/
./gradlew runIde       # sandbox IDE
```

Use a local IDE with `./gradlew buildPlugin -PlocalIdePath=/path/to/PhpStorm.app`, or set `localIdePath` in `~/.gradle/gradle.properties`.

## Disclaimer

Claudex Autocomplete is an independent project and is not affiliated with, endorsed or sponsored by Anthropic or OpenAI. Claude is a trademark of Anthropic; Codex is a trademark of OpenAI.

## License

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Release history is in [CHANGELOG.md](CHANGELOG.md).
