# Claudex Autocomplete for JetBrains

Copilot-style inline ghost-text completions for PhpStorm 2025.3+ (build 253+) and other JetBrains IDEs,
powered by the local Claude Code CLI or Codex CLI. It uses your Claude or ChatGPT subscription login, no API key needed.

## Providers

Settings > Tools > Claudex Autocomplete > Provider selects the engine:

- **Claude Code**: the `claude` CLI, logged in with your Claude subscription.
- **Codex**: the OpenAI `codex` CLI, logged in with your ChatGPT subscription. Install with
  `npm i -g @openai/codex` or `brew install codex`, then run `codex login` in a terminal.

You can also switch from the status bar widget menu (Provider submenu). The widget text, notifications and usage
tooltip follow the selected provider. Timeout, persistent process, custom prompt additions, context options and
debounce are shared by both.

## Requirements

- JetBrains IDE build 253 or newer.
- The `claude` CLI installed and logged in: run `claude` in a terminal, then `/login`.

## Install

1. Build the plugin: `./gradlew buildPlugin`
2. In the IDE: Settings > Plugins > gear icon > Install Plugin from Disk...
3. Select `build/distributions/claudex-autocomplete-0.1.0.zip` and restart the IDE.

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
that match the suggestion keeps it and trims it. Tools > Toggle Claudex Autocomplete, or a click on the status
bar widget, enables or disables it.

### Status bar widget

The bottom status bar shows `Claude: Ready`, `Waiting...` (debounce), `Thinking... Ns` (request in flight),
the latency of the last completion (for example `0.7s`, briefly), `Off`, `Error` or `Limit reached`. When usage
data is known it appends `5h 42% | 7d 18%`; a warning sign is shown once a window reaches 80%. Usage is read
from the CLI responses, so it appears after the first request (or after Refresh Usage / Test connection).
The tooltip lists both windows with reset times, last request latency and model. Click the widget for a menu:
Enable/Disable, Open Settings, Refresh Usage. When the CLI reports a usage limit, a single balloon is shown,
requests are paused until the reset time, and the status reads `Limit reached`. Usage in the status bar can
be hidden in settings.

While a request is in flight (after the debounce, and only once it has taken longer than about 150 ms) an animated
braille spinner appears at the end of the caret line in the same font and color as ghost text, with the elapsed
time (for example `1.8s`) once the request has taken more than 1.5 s. If no available font can draw braille it
falls back to animated dots. It never moves your text and disappears as soon as the suggestion arrives, you type, the caret moves or the request fails. Disable them with
"Show loading indicator in editor while generating" in settings.

Sending the whole file keeps suggestions accurate but makes requests bigger and slower. If latency matters,
switch Current file context to "lines around the cursor" with a smaller window. Imported class outlines come
from the bundled PHP plugin; in IDEs without it the option has no effect.

Completions are only requested when the text right of the caret on the current line is empty or only
closing characters (`)]}>"';,`), and never in read-only editors or files over 1,000,000 characters.

## Settings

Settings > Tools > Claudex Autocomplete.

| Field | Default | Notes |
|---|---|---|
| Enabled | true | |
| Provider | Claude Code | Claude Code or Codex |
| Claude CLI path | empty | empty = auto-detect (`~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`, `~/.claude/local`, login shell) |
| Model (Claude) | haiku | haiku, sonnet, opus, fable or a full model id |
| Fallback model | empty | passed as `--fallback-model` |
| Effort | low | low, medium, high, xhigh, max |
| Thinking | off | when on, uses the thinking budget |
| Thinking budget (tokens) | 1024 | |
| Debounce (ms) | 250 | 0..2000 |
| Request timeout (ms) | 8000 | |
| Current file context | auto | auto = whole file if at most 1000 lines, otherwise 150 lines above and below the cursor; or always the whole file; or always the lines around the cursor |
| Show loading indicator in editor | true | braille spinner and elapsed time at line end while generating |
| Show request state and usage in status bar | true | appends 5h / 7d usage to the widget text |
| Include open tabs | true | |
| Open tabs char budget | 6000 | split across tabs, most recent first |
| Multi-line mode | auto | auto / always / never |
| Max completion lines | 12 | |
| Codex CLI path | empty | empty = auto-detect |
| Model (Codex) | gpt-5.3-codex | any model id supported by your ChatGPT plan |
| Reasoning effort (Codex) | low | none, minimal, low, medium, high |
| Keep CLI process alive | true | persistent stream-json process, faster than one-shot |
| Include imported classes | true | PHP: outlines of `use`-imported classes, parent class, interfaces and traits (signatures only) |
| Imported classes char budget | 8000 | |
| Custom prompt additions | empty | multi-line, appended to the system prompt, e.g. "Follow PSR-12, prefer readonly properties" |
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
- **Codex not logged in**: run `codex login` in a terminal.
- **CLI not found**: set the full path in Settings > Tools > Claudex Autocomplete. GUI-launched IDEs have a minimal
  PATH, so auto-detection checks the usual install locations.
- **Invalid model**: pick one of haiku, sonnet, opus, fable or a valid full model id.
- **Slow completions**: use `haiku`, keep "Keep CLI process alive" on, keep effort `low` with thinking off, and
  lower the open tabs budget or prefix size.
- **Limit reached**: the Claude usage window is exhausted; completions resume automatically after the reset time shown in the widget tooltip.
- **No suggestions**: check the status bar widget (Ready / Thinking / Disabled / Error), the disabled
  languages list, and that the caret is not in the middle of code on the line.
