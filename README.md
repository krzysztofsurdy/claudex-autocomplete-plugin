# Claude Autocomplete for JetBrains

Copilot-style inline ghost-text completions for PhpStorm 2025.3+ (build 253+) and other JetBrains IDEs,
powered by the local Claude Code CLI. It uses your Claude subscription login, no API key needed.

## Requirements

- JetBrains IDE build 253 or newer.
- The `claude` CLI installed and logged in: run `claude` in a terminal, then `/login`.

## Install

1. Build the plugin: `./gradlew buildPlugin`
2. In the IDE: Settings > Plugins > gear icon > Install Plugin from Disk...
3. Select `build/distributions/claude-autocomplete-0.1.0.zip` and restart the IDE.

## Usage and keys

Completions appear as gray text after a short pause in typing.

| Action | Default shortcut |
|---|---|
| Accept whole suggestion | Tab |
| Accept next word | Alt+Right (macOS), Ctrl+Right (Windows/Linux) |
| Accept rest of line | Cmd+Right (macOS), End (Windows/Linux) |
| Dismiss | Esc |
| Trigger Claude manually | Alt+\ (ignores the mid-line rule and the cache) |
| Next / previous variant | Alt+] / Alt+[ (only if a provider returns several) |

The word and line accept actions reuse your keymap's Next Word and Line End shortcuts. Typing characters
that match the suggestion keeps it and trims it. Tools > Toggle Claude Autocomplete, or a click on the status
bar widget, enables or disables it.

Completions are only requested when the text right of the caret on the current line is empty or only
closing characters (`)]}>"';,`), and never in read-only editors or files over 1,000,000 characters.

## Settings

Settings > Tools > Claude Autocomplete.

| Field | Default | Notes |
|---|---|---|
| Enabled | true | |
| Claude CLI path | empty | empty = auto-detect (`~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`, `~/.claude/local`, login shell) |
| Model | haiku | haiku, sonnet, opus, fable or a full model id |
| Fallback model | empty | passed as `--fallback-model` |
| Effort | low | low, medium, high, xhigh, max |
| Thinking | off | when on, uses the thinking budget |
| Thinking budget (tokens) | 1024 | |
| Debounce (ms) | 250 | 0..2000 |
| Request timeout (ms) | 8000 | |
| Max prefix chars | 6000 | |
| Max suffix chars | 2000 | minimum 200 |
| Include open tabs | true | |
| Open tabs char budget | 6000 | split across tabs, most recent first |
| Multi-line mode | auto | auto / always / never |
| Max completion lines | 12 | |
| Keep CLI process alive | true | persistent stream-json process, faster than one-shot |
| Custom instructions | empty | appended to the system prompt, e.g. "Follow PSR-12" |
| Disabled languages | empty | comma separated language ids |

`ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` and `CLAUDE_CODE_*` environment variables are removed from the
CLI environment so your subscription login is always used. "Test connection" runs one completion with the
values currently in the form, without saving them.

## Build

```
export JAVA_HOME=/Users/krzysztof.surdy/Applications/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew test        # unit tests and headless IDE platform tests
./gradlew buildPlugin # zip in build/distributions/
./gradlew runIde      # sandbox IDE
```

## Troubleshooting

- **Not logged in**: run `claude` in a terminal, then `/login`. A balloon notification is shown at most once
  every 5 minutes per error kind.
- **CLI not found**: set the full path in Settings > Tools > Claude Autocomplete. GUI-launched IDEs have a minimal
  PATH, so auto-detection checks the usual install locations.
- **Invalid model**: pick one of haiku, sonnet, opus, fable or a valid full model id.
- **Slow completions**: use `haiku`, keep "Keep CLI process alive" on, keep effort `low` with thinking off, and
  lower the open tabs budget or prefix size.
- **No suggestions**: check the status bar widget (Ready / Thinking / Disabled / Error), the disabled
  languages list, and that the caret is not in the middle of code on the line.
