# Claudex Autocomplete - Spec

Inline ghost-text completions for JetBrains IDEs (build 253+), backed by the local Claude Code or Codex CLI. Uses the user's subscription login, no API key.

## Build

- JDK 21, Kotlin 2.2.20, IntelliJ Platform Gradle Plugin 2.19.0, Gradle 9.8.0.
- `./gradlew test`, `./gradlew buildPlugin`, `./gradlew runIde`. Set `localIdePath` to use a local IDE.
- Base package and plugin id: `dev.ksurdy.claudeautocomplete`.
- Only `com.intellij.modules.platform` is required. PHP support is an optional dependency (`claude-autocomplete-php.xml`).
- No coroutines dependency, the platform bundles it. JSON goes through the bundled Gson.

## Behaviour

1. Suggestion shows after a pause in typing (debounce, default 250 ms).
2. Accept, next word and dismiss come from the platform inline completion actions.
3. A new keystroke cancels the in-flight request.
4. Manual trigger: the platform's `CallInlineCompletionAction` (Shift+Alt+\). `Tools | Trigger Claudex Autocomplete` (`ClaudeAutocomplete.Trigger`) forces a completion mid-line, no default shortcut.
5. Suggest at the end of a line and mid-line, but not inside a word (letters, digits, `_` or `$` on both sides of the caret). Mid-line suggestions are single-line; an echoed rest of the line is trimmed and trailing spaces before it are kept.
6. Multi-line when the caret line is blank, or the rest of the line is closers and the prefix ends with `{`, `[` or `:` (not `::`). Otherwise single-line. Setting `multilineMode` overrides (auto/always/never). A multi-line request in an empty block body (prefix ends with `{`, suffix starts with `}`) asks for the complete body.
7. Post-processing: strip code fences, trim echoed head, cut to single line or `maxCompletionLines` (an over-long reply is cut at the last line where its brackets are balanced, hard cut only when there is none), shift continuation lines up to the caret indent on a blank caret line, trim overlapping tail and trailing whitespace.
8. Streaming: the CLI reports text deltas (`--include-partial-messages`, persistent and one-shot). Multi-line suggestions show complete lines up to the last point where brackets are balanced; the rest appears with the final result. Text already shown is never changed. Codex shows the final result only.
9. LRU cache, 32 entries, 60 s TTL. Key: provider, model settings, file, language, mode, last 500 chars of prefix, first 200 of suffix, imported classes.
10. No completions in read-only editors, files over 1,000,000 chars, or disabled languages.
11. Nothing runs until the user consents (`consentGiven`). First-run notification: Enable / Settings / Not now.
12. Failure notifications (group `Claude Autocomplete`): not logged in, CLI not found, invalid model, rate limited. Throttled to once per 5 min per provider and kind.
13. Files matching `excludedFilePatterns`, VCS-ignored files and files excluded from the project are never sent as context.
14. Alternatives: the platform's Next/Previous suggestion actions (Alt+] / Alt+[) cycle between up to 3 suggestions. Next on the last suggestion asks the model for another one (`alternativeTo` carries the texts already shown, rendered as `<shown_suggestions>`), bypassing the cache. The new suggestion comes first, the earlier ones follow as finished variants, so cycling back costs nothing. Nothing is requested before the user switches. Done by `NextAlternativeHandler`, an `editorActionHandler` wrapping the platform action; other providers' sessions go to the original handler.

## Settings

App-level `ClaudeAutocompleteSettings`, stored in `claude-autocomplete.xml`. UI: Settings | Tools | Claudex Autocomplete.

| Field | Default | Notes |
|---|---|---|
| enabled | true | Only active together with `consentGiven` (default false) |
| provider | claude | claude / codex |
| claudePath, codexPath | empty | Empty = auto-detect |
| model | haiku | haiku, sonnet, opus, fable or a full id |
| fallbackModel | empty | `--fallback-model` when set |
| effort | low | `--effort` |
| thinkingEnabled | false | false = `MAX_THINKING_TOKENS=0` |
| thinkingBudgetTokens | 1024 | Used when thinking is on |
| codexModel | gpt-5.3-codex | |
| codexReasoningEffort | low | none, minimal, low, medium, high |
| debounceMs | 250 | |
| requestTimeoutMs | 8000 | |
| contextMode | auto | auto / wholeFile / linesAround |
| wholeFileMaxLines | 1000 | Auto sends the whole file up to this many lines |
| linesAroundCursor | 150 | Lines above and below the caret when windowed |
| includeOpenTabs, maxOpenTabsChars | true, 6000 | Budget split across tabs, newest first |
| includeImportedClasses, maxImportedClassesChars | true, 8000 | PHP: imported classes, parent, interfaces, traits |
| showInlineLoadingIndicator | true | Braille spinner with elapsed time |
| showUsageInStatusBar | true | |
| multilineMode | auto | |
| maxCompletionLines | 30 | |
| persistentProcess | true | Claude Code only |
| customInstructions | empty | Appended to the system prompt |
| disabledLanguages | empty | Comma-separated language ids |
| excludedFilePatterns | `SensitiveFiles.DEFAULT_PATTERNS` | |

## Request

User message, built by `PromptBuilder`:

```
<open_files>...</open_files>             only with open tabs
<imported_classes>...</imported_classes> only for PHP with imports
<file path="..." language="..." indent="...">
prefix<CURSOR/>suffix
</file>
<shown_suggestions>...</shown_suggestions>  only for alternatives
<mode>single-line | multi-line (max N lines) | body (max N lines)</mode>
```

The file is whole or a window around the caret, capped at 200k chars. A truncated side gets a `<!-- truncated -->` marker. The fixed system prompt makes the model a fill-in-the-middle engine: raw text only, no fences, no repeating text around the caret. `customInstructions` is appended.

## Claude Code CLI

Flags: `-p --model <m> --safe-mode --tools "" --no-session-persistence --strict-mcp-config --setting-sources "" --system-prompt <sys> --effort <e> [--fallback-model <f>]`

- One-shot: `--output-format stream-json --verbose --disable-slash-commands`, prompt on stdin.
- Persistent: `--input-format stream-json --output-format stream-json --verbose --include-partial-messages`. Per request: send `/clear`, wait for its `result`, send the prompt, collect text deltas until `result`. Cancel with a `control_request` of subtype `interrupt`. One request at a time (mutex).
- Warm-up: when a project opens (and right after consent) the persistent process is started without sending a prompt, so the first request skips the CLI start. Not for Codex or one-shot mode.
- On timeout or process death the process is killed and respawned lazily. After repeated persistent failures it falls back to one-shot. Config change restarts the process.
- Never `--bare`, it breaks subscription auth.
- Env: `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN`, `CLAUDECODE` and `CLAUDE_CODE_*` removed. Added `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`, `DISABLE_TELEMETRY=1`, `MAX_THINKING_TOKENS`. PATH gets the binary dir plus `/opt/homebrew/bin:/usr/local/bin`.
- Working dir is a private temp dir, so no project `CLAUDE.md` is loaded.

## Codex CLI

- Completions: one `codex exec --json --skip-git-repo-check --ephemeral -s read-only -m <model>` per request, plus `-c` overrides (reasoning effort, developer instructions, no environment context, no web search). No persistent process.
- Usage windows: `codex app-server`, read on refresh.
- Env: `OPENAI_API_KEY`, `CODEX_API_KEY`, `CLAUDECODE` and `CLAUDE_CODE_*` removed.

## Failures

`FailureKind`: NotLoggedIn, CliNotFound, InvalidModel, Timeout, RateLimited, Other. Mapped from CLI output by `FailureMapper` and `CodexFailureMapper`.

## Layout

- `backend/` - CLI processes, command builders, parsers, prompt, usage tracking, `CompletionRouter` (picks the provider).
- `completion/` - `TriggerRules`, `CompletionPostProcessor`, `CompletionCache`. Pure logic.
- `php/` - class outlines and imported-class context.
- `alternatives/` - Next-suggestion handler, pending alternative request, multi-variant suggestion.
- `actions/`, `ui/` - toggle and trigger actions, status bar widget.
- top level - inline completion provider, settings, context collection, status service, consent prompt.
