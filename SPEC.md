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
7. Post-processing: take the content of the first `<completion>` block (see Request), strip code fences, trim echoed head, cut to single line or `maxCompletionLines` (an over-long reply is cut at the last line where its brackets are balanced, hard cut only when there is none), shift continuation lines up to the caret indent on a blank caret line, trim overlapping tail and trailing whitespace. Without an opening `<completion>` tag (an older model, Codex ignoring it) the whole reply is used, after a prose guard: a reply whose first non-blank line reads like natural language (chat openers such as "The cursor", "Wait", "Here", "Sure", "Let me", "I'll", a mention of `<CURSOR/>` or of the cursor in a sentence, or a plain sentence of words ending in punctuation with no code tokens) gives no suggestion, and chat lines after the code are cut off. Comments and strings are code: the guard ignores lines starting with a comment marker, relaxes while the caret sits inside a comment or string, and is off for Markdown and plain text files.
8. Streaming: the CLI reports text deltas (`--include-partial-messages`, persistent and one-shot). Multi-line suggestions show complete lines up to the last point where brackets are balanced; the rest appears with the final result. Only text inside `<completion>` is shown: nothing before the tag, never the closing tag or what follows, and a partial `</compl` at the end is held back. Bare replies stream through the prose guard. Text already shown is never changed. Codex shows the final result only.
9. LRU cache, 32 entries, 60 s TTL. Key: provider, model settings, file, language, mode, last 500 chars of prefix, first 200 of suffix, imported classes.
10. No completions in read-only editors, files over 1,000,000 chars, or disabled languages.
11. Nothing runs until the user consents (`consentGiven`). First-run notification: Enable / Settings / Not now.
12. Failure notifications (group `Claude Autocomplete`): not logged in, CLI not found, invalid model, rate limited. Throttled to once per 5 min per provider and kind.
13. Files matching `excludedFilePatterns`, VCS-ignored files and files excluded from the project are never sent as context.
14. Alternatives: the platform's Next/Previous suggestion actions (Alt+] / Alt+[) move through up to 3 suggestions in the order they arrived, wrapping at the ends. Next on the newest suggestion, with fewer than 3 existing, asks the model for another one (`alternativeTo` carries the texts already shown, rendered as `<shown_suggestions>`), bypassing the cache. The reply is not streamed: it is compared with the shown texts ignoring whitespace and trailing `;`, `,` or `.`. A duplicate is asked for once more with a `<retry>` note after `<shown_suggestions>` (`distinctRetry`); a second duplicate, an empty reply or a failure adds no variant and the existing ones stay. A duplicate is never shown. Nothing is requested before the user asks. The session lists variants newest first, so `NextAlternativeHandler` and `PrevAlternativeHandler` (`editorActionHandler`s on the platform actions) swap the platform's direction for our sessions and delegate to the original handler for any other.
15. Next edit suggestions (`nextEditSuggestions`, default off, needs `enabled` and `consentGiven`): a document listener per editor keeps the last 5 edits as small line hunks (at most 12 lines and 600 chars per side, typing on one line merges into one hunk, hunks below an insertion or deletion are renumbered, secret files are not tracked). After a pause of `2 * debounceMs` with no edit, one request goes through the same engine and provider as completions. Any edit cancels the in-flight request and restarts the pause. No request while ghost text is visible, at a usage limit, in read-only editors, disabled languages or excluded files. The answer is dropped if the document or caret moved. An edit touching the caret line, outside the file, longer than 20 lines or identical to the current text is dropped.
16. A shown suggestion highlights and strikes through the target lines and adds a block inlay below them with the replacement and a hint. Tab (an `EditorTab` handler, first in the chain) jumps the caret to the start of the target, Tab again applies it in one write command and moves the caret to the end of the new text. Esc, any document change or caret move dismisses. While ghost text is visible the handlers step aside, so the inline completion Tab and Esc win. With nothing shown Tab and Esc behave as before. `NextEditController` writes debug lines (`Requesting next edit`, `Next edit received|empty|failed|cancelled|dropped|shown|jumped|applied|dismissed`) with timings, line ranges, counts and drop reasons only, never code (`NextEditLog`).

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
| nextEditSuggestions | false | Next edit suggestions, an extra request per edit and pause |
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
<retry>...</retry>                          only when an alternative repeated a shown one
<mode>single-line | multi-line (max N lines) | body (max N lines)</mode>
```

The file is whole or a window around the caret, capped at 200k chars. A truncated side gets a `<!-- truncated -->` marker. The fixed system prompt makes the model a fill-in-the-middle engine. Reply contract: the insertion is wrapped as `<completion>TEXT</completion>`, raw code only inside, no fences, no repeating text around the caret; anything outside the tags is ignored, and `<completion></completion>` means nothing fits (the file is finished, no code can go at the caret). On a blank line inside a class, interface, trait or function body the prompt asks for the most likely next member or statement instead, with an example of a method between two methods. Claude Code and Codex get the same prompt. `customInstructions` is appended.

### Next edit request

Same system prompt and engine. The user message has no `<CURSOR/>` and ends with `<mode>next-edit</mode>`. Open tabs and imported classes are not sent.

```
<recent_edits>
<edit line="5">
<before>
old line(s)
</before>
<after>
new line(s)
</after>
</edit>
</recent_edits>
<numbered_file path="..." language="..." indent="...">
1|first line
2|second line
</numbered_file>
<caret line="5"/>
<mode>next-edit</mode>
```

The file is whole or a window by the same context settings, every line prefixed `N|` with its own text right after the bar. The system prompt describes the mode and the only valid replies: `<edit start_line="N" end_line="M">replacement</edit>` (replaces lines N to M whole, an empty replacement deletes them) or `<none/>`. `NextEditParser` takes the first `<edit>` and tolerates chatter, code fences, other quote styles and attribute order, a missing end line and echoed line numbers. `NextEditTarget` maps lines to offsets, restores a dropped first-line indent and applies the drops from Behaviour 14.

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
- `nextedit/` - edit history, next edit prompt section, reply parser, target choice and state (pure), plus the editor controller, preview, Tab and Esc handlers.
- `php/` - class outlines and imported-class context.
- `alternatives/` - Next-suggestion handler, pending alternative request, multi-variant suggestion.
- `actions/`, `ui/` - toggle and trigger actions, status bar widget.
- top level - inline completion provider, settings, context collection, status service, consent prompt.
