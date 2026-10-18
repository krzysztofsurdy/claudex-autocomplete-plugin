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
6. Multi-line when the caret line is blank, or the rest of the line is closers and the prefix ends with `{`, `[`, `(` or `:` (not `::`). Otherwise single-line. Setting `multilineMode` overrides (auto/always/never). A multi-line request in an empty block body (prefix ends with `{`, suffix starts with `}`) asks for the complete body. A multi-line request with the caret at the end of an open function or method signature (the caret line is a declaration with `function`, `fun`, `def`, `func` or `fn`, or starts with modifiers such as `public` or `static`, ends with `(`, and nothing follows the caret on the line) asks for the whole method: the rest of the parameters, the return type, the body and the closing brace.
7. Post-processing: take the content of the first `<completion>` block (see Request), strip code fences, trim echoed head, cut to single line or `maxCompletionLines` (an over-long reply is cut at the last line where its brackets are balanced; when there is none and the suffix does not start with a closer, the reply is kept up to the line that closes its first block, and in `declaration` mode always up to the end of the method; hard cut only otherwise), shift continuation lines up to the caret indent on a blank caret line, trim overlapping tail and trailing whitespace (a closer is trimmed only when the reply has an unmatched closer of that same kind, so the closing `}` of a completed method is kept even though its `)` closes the signature). Three quality guards then cut the result: a repetition guard cuts a reply that ends in a loop (the last tokens repeat a pattern of up to 40 tokens, checked with and without whitespace tokens, from a window of 24 tokens for one token up to 160 for long patterns, 120 for runs of symbols) back to the last complete line before the loop, or drops it when nothing is left; a next-line guard drops the suggestion, or its last lines, when they only repeat the next non-empty lines below the caret (compared trimmed, the caret line included, no standalone closer lines, no tiny lines, never leaving a block open); a closer guard removes trailing closer lines (`}`, `]`, `)`, `});`, `end`, `fi`, `done`) that already exist below the caret, also behind blank lines and with other indentation, only as many as the suggestion leaves unmatched per bracket kind, so `} else {` keeps its brace and the closer of the suggestion's own block stays. The next-line and closer guards apply only when nothing but whitespace follows the caret on its line. Every cut is logged at debug level as `Completion guarded: guard=... linesBefore=... linesAfter=...`, and an emptied result carries the guard as `cause` in `Completion emptied`, never any code. Without an opening `<completion>` tag (an older model, Codex ignoring it) the whole reply is used, after a prose guard: a reply whose first non-blank line reads like natural language (chat openers such as "The cursor", "Wait", "Here", "Sure", "Let me", "I'll", a mention of `<CURSOR/>` or of the cursor in a sentence, or a plain sentence of words ending in punctuation with no code tokens) gives no suggestion, and chat lines after the code are cut off. Comments and strings are code: the guard ignores lines starting with a comment marker, relaxes while the caret sits inside a comment or string, and is off for Markdown and plain text files.
8. Streaming: the CLI reports text deltas (`--include-partial-messages`, persistent and one-shot). Multi-line suggestions show complete lines up to the last point where brackets are balanced; the rest appears with the final result. Only text inside `<completion>` is shown: nothing before the tag, never the closing tag or what follows, and a partial `</compl` at the end is held back. Bare replies stream through the prose guard. Text already shown is never changed: the guards run on the streamed text too, and tail lines that could still be cut (the start of a repetition, lines that begin to repeat the lines below the caret) are held back until the reply goes on differently or ends. A loop is recognised once it repeats, so at most its first line can already be on screen. Codex shows the final result only.
9. LRU cache, 32 entries, 60 s TTL. Key: provider, model settings, file, language, mode, last 500 chars of prefix, first 200 of suffix, imported classes.
10. No completions in read-only editors, files over 1,000,000 chars, or disabled languages.
11. Nothing runs until the user consents (`consentGiven`). First-run notification: Enable / Settings / Not now.
12. Failure notifications (group `Claude Autocomplete`): not logged in, CLI not found, invalid model, rate limited. Throttled to once per 5 min per provider and kind.
13. Files matching `excludedFilePatterns`, VCS-ignored files and files excluded from the project are never sent as context.
14. Alternatives: the platform's Next/Previous suggestion actions (Alt+] / Alt+[) move through up to 3 suggestions in the order they arrived, wrapping at the ends. Next on the newest suggestion, with fewer than 3 existing, asks the model for another one (`alternativeTo` carries the texts already shown, rendered as `<shown_suggestions>`), bypassing the cache. The reply is not streamed: it is compared with the shown texts ignoring whitespace and trailing `;`, `,` or `.`. A duplicate is asked for once more with a `<retry>` note after `<shown_suggestions>` (`distinctRetry`); a second duplicate, an empty reply or a failure adds no variant and the existing ones stay. A duplicate is never shown. Nothing is requested before the user asks. The session lists variants newest first, so `NextAlternativeHandler` and `PrevAlternativeHandler` (`editorActionHandler`s on the platform actions) swap the platform's direction for our sessions and delegate to the original handler for any other.
15. Next edit suggestions (`nextEditSuggestions`, default off, needs `enabled` and `consentGiven`): a document listener per editor keeps the last 5 edits as small line hunks (at most 12 lines and 600 chars per side, typing on one line merges into one hunk, hunks below an insertion or deletion are renumbered, secret files are not tracked). After a pause of `2 * debounceMs` with no edit, one request goes through the same engine and provider as completions. Any edit cancels the in-flight request and restarts the pause. No request while ghost text is visible, at a usage limit, in read-only editors, disabled languages or excluded files. The answer is dropped if the document or caret moved. An edit touching the caret line, outside the file, longer than 20 lines or identical to the current text is dropped.
16. A shown suggestion highlights and strikes through the target lines and adds a block inlay below them with the replacement and a hint. Tab (an `EditorTab` handler, first in the chain) jumps the caret to the start of the target, Tab again applies it in one write command and moves the caret to the end of the new text. Esc, any document change or caret move dismisses. While ghost text is visible the handlers step aside, so the inline completion Tab and Esc win. With nothing shown Tab and Esc behave as before. `NextEditController` writes debug lines (`Requesting next edit`, `Next edit received|empty|failed|cancelled|dropped|shown|jumped|applied|dismissed`) with timings, line ranges, counts and drop reasons only, never code (`NextEditLog`).
17. Status bar widget (`StatusBarWidget.IconPresentation`, no text): the Claudex icon, grey when completions are off (`enabled` false or no `consentGiven`) or the status is Error or LimitReached, coloured when Ready, Waiting or Done, and alternating between coloured and grey every 500 ms while the status is Thinking. The grey icon is `IconLoader.getDisabledIcon` of the logo, so it follows the theme. A Swing timer runs only while Thinking and is stopped on any other status and in `dispose()`. The tooltip carries provider, state, last latency, model and the 5h / 7d usage windows. Click opens the menu (Enable/Disable, Provider, Open settings, Refresh Usage).
18. Suggestion hint: the platform's "Tab to complete" hint carries `SuggestionHint` (the provider's `providerPresentation`): the Claudex icon and "Claudex · <Provider> <model>" from the message bundle, never the provider id.
19. Trigger gates: `CompletionGates` (pure) decides from an `EditorSnapshot` that `EditorGates` builds in a read action, in `isEnabled` and once more after the debounce (without the duplicate check). A skipped request logs `Request skipped: gate=<name>` at debug level, the gate name only. Editor gates apply to every request, manual ones included: `Viewer`, `ReadOnly`, `OneLine`, `Embedded` (editor in a dialog or popup), `InjectedFragment`, `UnsupportedKind` (only main and untyped editors are served, so consoles, diff and preview editors are not), `MultiCaret` (the platform builds no request for several carets). State gates apply to automatic requests (typing) only, DirectCall and ManualCall pass them: `UndoRedo`, `BulkUpdate`, `Selection` (any caret), `LiveTemplate` (`TemplateManager.getActiveTemplate` is set, or the current command is the template insertion command or the Expand Live Template by Tab action), `LookupOpen` (unless `suggestWhileLookupOpen`; the request is skipped, not queued, so the next keystroke after the popup closes asks again), `NextEditShown` (`NextEditState.isVisible`) and `Duplicate` (same document modification stamp and caret offset as the last request that passed the gates in this editor; the same event asked twice by the platform is not its own duplicate). Order: editor gates, state gates, duplicate.

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
| includeOpenTabs, maxOpenTabsChars | true, 6000 | Related snippets from open tabs (labelled "Include related snippets from open tabs"); the budget is the total characters of all snippets |
| includeImportedClasses, maxImportedClassesChars | true, 8000 | PHP: imported classes, parent, interfaces, traits |
| showInlineLoadingIndicator | true | Braille spinner with elapsed time |
| suggestWhileLookupOpen | false | false = typing waits for the code-completion popup to close, manual triggers always run |
| nextEditSuggestions | false | Next edit suggestions, an extra request per edit and pause |
| multilineMode | auto | |
| maxCompletionLines | 30 | |
| persistentProcess | true | Claude Code only |
| customInstructions | empty | Appended to the system prompt |
| disabledLanguages | empty | Comma-separated language ids |
| excludedFilePatterns | `SensitiveFiles.DEFAULT_PATTERNS` | |

## Request

User message, built by `PromptBuilder`:

```
<related_snippets>...</related_snippets> only with related open-tab code
<imported_classes>...</imported_classes> only for PHP with imports
<file path="..." language="..." indent="...">
prefix<CURSOR/>suffix
</file>
<shown_suggestions>...</shown_suggestions>  only for alternatives
<retry>...</retry>                          only when an alternative repeated a shown one
<mode>single-line | multi-line (max N lines) | body (max N lines) | declaration (max N lines)</mode>
```

Each `<snippet path="src/Foo.php" lines="40-99">` holds whole lines of one open file with its 1-based inclusive line range, the most relevant snippet last (closest to the current file). The file is whole or a window around the caret, capped at 200k chars. A truncated side gets a `<!-- truncated -->` marker. The fixed system prompt makes the model a fill-in-the-middle engine. Reply contract: the insertion is wrapped as `<completion>TEXT</completion>`, raw code only inside, no fences, no repeating text around the caret; anything outside the tags is ignored, and `<completion></completion>` means nothing fits (the file is finished, no code can go at the caret). On a blank line inside a class, interface, trait or function body the prompt asks for the most likely next member or statement instead, with an example of a method between two methods. Modes: `body` is an empty block, `declaration` an open signature (it wins over `body`); both are only sent in multi-line mode. Rules against lazy output: never placeholder code (TODO or FIXME stubs, "not implemented" throws, `pass`, `...`, an empty body standing in for real logic, a comment saying to implement it later); write the real implementation inferred from names, signature and similar code in the file and related snippets; complete the construct the cursor is in first, such as the parameter list of an open signature, before anything after it, and in `declaration` mode carry on through the return type, the body and the closing brace (the prompt carries an example of an open signature completed into the whole method). Claude Code and Codex get the same prompt. `customInstructions` is appended.

### Related snippets

Built by `ContextCollector` through `SnippetSelector` (`context/`) when `includeOpenTabs` is on. Candidates are the open tabs except the current file, minus excluded, sensitive, binary and over-long files, newest first.

1. Query: the last 60 lines before the caret plus the rest of the current line (`CaretQuery`). Its distinct identifier tokens (`[letter_][letter digit _]*`, at least 2 characters, case kept, `$` not part of the token) minus stop words are the query set. Stop words are a small generic list plus keywords of the language family (`StopWords`: scripting languages Python, Ruby and shell, C-style for everything else).
2. Windows: each file is tokenised once (first 5000 lines, first 400 characters of a line) and cut into windows of 60 lines with a step of 5, plus one window flush with the end. A sliding multiset of token ids moves from window to window, so a file costs at most two operations per token.
3. Score: Jaccard, shared distinct tokens over the union of the query set and the window's distinct tokens. Best window per file, the earliest wins a tie. A window needs a score of at least 0.1 and 2 shared tokens.
4. Selection: files of the current language first, files of other languages only for slots and budget still left. At most 4 snippets, best first, each within what is left of `maxOpenTabsChars`. A window that does not fit is cut to whole lines from its start and dropped when fewer than 3 lines remain.
5. Order in the prompt: ascending score, the best snippet last.

### Next edit request

Same system prompt and engine. The user message has no `<CURSOR/>` and ends with `<mode>next-edit</mode>`. Related snippets and imported classes are not sent.

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
- `completion/` - `TriggerRules`, `CompletionGates`, `CompletionPostProcessor`, `CompletionCache`. Pure logic.
- `context/` - related snippet ranking: identifier tokenizer, stop words, window scorer, selector. Pure logic.
- `nextedit/` - edit history, next edit prompt section, reply parser, target choice and state (pure), plus the editor controller, preview, Tab and Esc handlers.
- `php/` - class outlines and imported-class context.
- `alternatives/` - Next-suggestion handler, pending alternative request, multi-variant suggestion.
- `actions/`, `ui/` - toggle and trigger actions, status bar widget.
- top level - inline completion provider, `EditorGates` (editor snapshot for the gates), settings, context collection, status service, consent prompt.
