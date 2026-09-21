<img src="src/main/resources/META-INF/pluginIcon.svg" width="80" alt="Claudex Autocomplete logo">

# Claudex Autocomplete

Copilot-style inline ghost-text completions for PhpStorm and other JetBrains IDEs, powered by your local Claude Code CLI or Codex CLI. It uses your existing Claude or ChatGPT subscription login. No API key needed.

## Features

- Inline gray-text suggestions after a short pause in typing, in any language the IDE edits.
- Two providers: Claude Code (`claude`) and Codex (`codex`), switchable from settings or the status bar.
- Accept a whole suggestion, the next word, or the rest of the line.
- Context-aware: current file (whole or a window around the caret), open tabs, and for PHP the outlines of imported classes, parent class, interfaces and traits.
- Persistent CLI process for lower latency (Claude Code).
- Animated braille spinner with elapsed time at the end of the line while a suggestion is generated.
- Status bar widget with request state, last latency and 5h / 7d usage windows.
- Custom prompt additions, for example "Follow PSR-12".

## Requirements

- JetBrains IDE build 253 or newer (2025.3+).
- One of:
  - Claude Code CLI (`claude`), logged in with your Claude subscription.
  - Codex CLI (`codex`), logged in with your ChatGPT subscription.

## Installation

The plugin is not published on JetBrains Marketplace yet. Install from disk:

1. Build it: `./gradlew buildPlugin` (see [Building from source](#building-from-source)).
2. In the IDE: Settings > Plugins > gear icon > Install Plugin from Disk...
3. Select `build/distributions/claudex-autocomplete-0.1.0.zip` and restart the IDE.

## Quick start

1. Log in to a CLI in a terminal:
   - Claude: run `claude`, then `/login`.
   - Codex: `npm i -g @openai/codex` (or `brew install codex`), then `codex login`.
2. Open Settings > Tools > Claudex Autocomplete and pick the Provider.
3. Click Test connection, then start typing in an editor.

## Keyboard shortcuts

| Action | Shortcut |
|---|---|
| Accept whole suggestion | Tab |
| Accept next word | Alt+Right (macOS), Ctrl+Right (Windows/Linux) |
| Accept rest of line | Cmd+Right (macOS), End (Windows/Linux) |
| Dismiss | Esc |
| Trigger a completion manually | Alt+\ |

Word and line accept reuse your keymap's Next Word and Line End shortcuts. Tools > Toggle Claudex Autocomplete (or a click on the status bar widget) enables or disables the plugin.

## Settings

Settings > Tools > Claudex Autocomplete. The most useful options:

| Setting | Default | Notes |
|---|---|---|
| Provider | Claude Code | Claude Code or Codex |
| Claude CLI path / Codex CLI path | empty | Empty = auto-detect common install locations |
| Model (Claude) | haiku | haiku, sonnet, opus, fable or a full model id |
| Model (Codex) | gpt-5.3-codex | Any model supported by your ChatGPT plan |
| Effort / Reasoning effort | low | Lower is faster |
| Debounce (ms) | 250 | Delay after typing before a request |
| Request timeout (ms) | 8000 | |
| Current file context | auto | Whole file up to 1000 lines, otherwise 150 lines around the caret |
| Include open tabs | true | Character budget configurable |
| Include imported classes | true | PHP only, signatures only |
| Multi-line mode | auto | auto / always / never |
| Keep CLI process alive | true | Claude Code only; Codex starts a process per request |
| Custom prompt additions | empty | Appended to the system prompt |
| Disabled languages | empty | Comma-separated language ids |

"Test connection" runs one completion with the values currently in the form, without saving them.

## How it works

After you pause typing, the plugin sends the CLI the code before and after the caret, a window or the whole of the current file, optionally snippets of other open tabs and (for PHP) outlines of imported classes. With Claude Code the CLI runs as a persistent process, so there is no startup cost per request; Codex runs once per request. The result is shown as ghost text.

Completions are requested only when the text right of the caret on the line is empty or only closing characters, and never in read-only editors or files over 1,000,000 characters. `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` and `CLAUDE_CODE_*` environment variables are removed from the CLI environment so your subscription login is always used.

Your code context is sent to Anthropic or OpenAI through the respective CLI, subject to your account's terms.

## Status bar

The widget shows `Claude: Ready`, `Waiting...`, `Thinking... Ns`, the last latency, `Off`, `Error` or `Limit reached`, plus usage like `5h 42% · 7d 18%` once known. The tooltip lists reset times and the model. Click it for Enable/Disable, Provider, Open Settings and Refresh Usage. When a usage limit is hit, requests pause until the reset time.

## Troubleshooting

- **Not logged in**: run `claude` then `/login`, or `codex login`, in a terminal.
- **CLI not found**: set the full path in settings. GUI-launched IDEs have a minimal PATH.
- **Slow completions**: use `haiku`, keep "Keep CLI process alive" on, effort `low`, thinking off, and reduce the open tabs budget or file context window.
- **No suggestions**: check the status bar widget, the disabled languages list, and that the caret is at the end of the code on its line.
- **Limit reached**: completions resume automatically after the reset time shown in the widget tooltip.

## Building from source

Requires a local PhpStorm 2025.3+ install; its bundled JetBrains Runtime is used as the JDK.

```
export JAVA_HOME=/path/to/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew test         # unit tests and headless IDE platform tests
./gradlew buildPlugin  # zip in build/distributions/
./gradlew runIde       # sandbox IDE
```

Point the build at your IDE with `./gradlew buildPlugin -PlocalIdePath=/path/to/PhpStorm.app` (or set `localIdePath` in `~/.gradle/gradle.properties`).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for bug reports, setup, tests and commit conventions.
