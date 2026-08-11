# Claude Autocomplete for JetBrains - Spec

Copilot-style inline ghost-text completion for PhpStorm 2025.3+ (build 253+), backed by the local
Claude Code CLI so it uses the user's Claude subscription (OAuth, no API key).

## Build

- `export JAVA_HOME=/Users/krzysztof.surdy/Applications/PhpStorm.app/Contents/jbr/Contents/Home`
- `./gradlew test`, `./gradlew buildPlugin` (zip in `build/distributions/`), `./gradlew runIde`
- Kotlin 2.2.20, IntelliJ Platform Gradle Plugin 2.19.0, Gradle 9.8.0, JVM 21, `local(PhpStorm.app)`.
- Never add a kotlinx-coroutines dependency (bundled by the platform). Tests: JUnit 5 + kotlin-test.
- Base package: `dev.ksurdy.claudeautocomplete`. Plugin id: `dev.ksurdy.claudeautocomplete`.
- Only `com.intellij.modules.platform` dependency (works in every JetBrains IDE, not only PhpStorm).

## Copilot behaviours to reproduce

1. Gray text appears automatically after a pause in typing (debounce, default 250 ms).
2. Tab accepts all; next-word / next-line partial accept and Esc dismiss come from the platform
   (`InlineCompletion` action group) - do not reimplement.
3. Typing characters that match the suggestion keeps it and trims it (platform default update manager).
4. Every keystroke cancels the in-flight request (DebouncedInlineCompletionProvider + interrupt CLI).
5. Manual trigger action `Alt+\` (Copilot's shortcut) - `ClaudeAutocomplete.Trigger`.
6. Mid-line rules: only suggest when the text right of the caret on the current line is empty or
   only closing chars / whitespace (`)]}>"';,` etc.). Otherwise no request.
7. Single-line vs multi-line: multi-line only when the caret line is blank (only whitespace before
   caret), or the caret is at end of line (rest only closers/whitespace) and the prefix ends (ignoring
   spaces/tabs) with `{`, `[` or a python-style block `:` (not `::`); otherwise single-line (truncate
   at first newline). `(`, `->`, `=>` and function signatures no longer count as openers.
8. Strip overlap: if the completion ends with text that already follows the caret (suffix), trim
   the duplicated tail; if the completion starts with text already left of the caret (model echoed
   the prefix's last line), trim the echoed head.
9. Small LRU cache (32 entries) keyed by hash(prefix tail + suffix head + model) so going back/forth
   re-shows the same suggestion without a CLI call.
10. Status bar widget: shows state (Ready / Thinking / Disabled / Error: not logged in). Click
    toggles enabled globally. Tools menu action `ClaudeAutocomplete.Toggle`.
11. Disabled per language list; no completions in read-only/viewer editors, in files larger than
    the cap, or when a lookup popup is not the trigger.
12. Error surfacing: one balloon notification (group `Claude Autocomplete`) on "Not logged in"
    (tell user to run `claude` then `/login` in a terminal), CLI not found, or invalid model.
    Throttle to once per 5 minutes per error kind. Never spam.

## Settings (app-level, `ClaudeAutocompleteSettings`, Settings > Tools > Claude Autocomplete)

| Field | Type | Default | Notes |
|---|---|---|---|
| enabled | Boolean | true | |
| claudePath | String | "" | empty = auto-detect: `~/.local/bin/claude`, `/opt/homebrew/bin/claude`, `/usr/local/bin/claude`, `~/.claude/local/claude`, then `$SHELL -lc 'command -v claude'` |
| model | String | "haiku" | combo: haiku, sonnet, opus, fable, or any full model id (editable) |
| fallbackModel | String | "" | passed as `--fallback-model` when non-empty |
| effort | String | "low" | low, medium, high, xhigh, max -> `--effort` |
| thinkingEnabled | Boolean | false | false -> env `MAX_THINKING_TOKENS=0` |
| thinkingBudgetTokens | Int | 1024 | used as `MAX_THINKING_TOKENS` when thinking enabled |
| debounceMs | Int | 250 | 0..2000 |
| requestTimeoutMs | Int | 8000 | hard timeout per completion |
| maxPrefixChars | Int | 6000 | |
| maxSuffixChars | Int | 2000 | |
| includeOpenTabs | Boolean | true | |
| maxOpenTabsChars | Int | 6000 | total budget, split across tabs, most recently selected first |
| multilineMode | String | "auto" | auto / always / never |
| maxCompletionLines | Int | 12 | |
| persistentProcess | Boolean | true | keep one CLI process alive (stream-json), else spawn per request |
| customInstructions | String | "" | appended to the system prompt (e.g. "Follow PSR-12") |
| disabledLanguages | String | "" | comma separated language ids, case-insensitive |

## What each request contains (user message)

```
<file path="src/Foo/Bar.php" language="PHP">
...prefix...<CURSOR/>...suffix...
</file>
<open_files>  (only if includeOpenTabs and any)
<file path="..." language="...">...truncated content...</file>
</open_files>
<mode>single-line|multi-line (max N lines)</mode>
```
Plus a fixed system prompt (`--system-prompt`) describing the job: act as an inline code completion
engine (fill-in-the-middle), output ONLY the raw text to insert at <CURSOR/>, no markdown fences, no
explanations, never repeat text before the cursor or after it, keep indentation consistent, empty
output if nothing sensible; plus customInstructions.

## CLI invocation (verified on claude 2.1.289)

Flags: `-p --model <m> --safe-mode --tools "" --no-session-persistence --strict-mcp-config
--setting-sources "" --system-prompt <sys> --effort <e> [--fallback-model <f>]`
- one-shot mode: add `--output-format json --disable-slash-commands`, prompt on stdin, parse
  `{"type":"result","is_error":..,"result":"..."}`.
- persistent mode: add `--input-format stream-json --output-format stream-json --verbose
  --include-partial-messages` (NOT `--disable-slash-commands`, we need `/clear`). Per completion:
  write `{"type":"user","message":{"role":"user","content":"/clear"}}` and wait for its `result`,
  then write the prompt as a user message, collect `stream_event` -> `content_block_delta` ->
  `delta.text_delta.text`, finish on `{"type":"result"}`. Cancel with
  `{"type":"control_request","request_id":"<uuid>","request":{"subtype":"interrupt"}}` then drain
  until `result`. One request at a time (Mutex). If the process dies or times out, kill it and
  respawn lazily; if persistent mode fails twice in a row fall back to one-shot.
  The system prompt is fixed per process; restart the process when settings change.
- Never use `--bare` (breaks subscription auth).
- Env: start from the IDE env, REMOVE `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN`, `CLAUDECODE`,
  and every `CLAUDE_CODE_*` var; ADD `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`,
  `DISABLE_TELEMETRY=1`, `MAX_THINKING_TOKENS` (see settings). Ensure PATH includes the claude
  binary's dir plus `/opt/homebrew/bin:/usr/local/bin` (GUI apps get a minimal PATH).
- Working dir: a private temp dir (never the project dir, so no CLAUDE.md is loaded).
- Errors: exit 1 / `is_error:true`. Map result text containing "Not logged in" -> NotLoggedIn,
  "issue with the selected model" -> InvalidModel, binary missing -> CliNotFound, else Other.
- Expected latency: ~1 s one-shot, ~0.6 s persistent (haiku).

## Module layout and contracts (two agents work in parallel - stay in your files)

### Backend (agent A) - `dev.ksurdy.claudeautocomplete.backend` + `...completion` pure logic
No IntelliJ APIs except `com.intellij.openapi.diagnostic.Logger` and `kotlinx.coroutines`; fully unit-testable.

```kotlin
data class CompletionContext(
    val filePath: String, val languageId: String,
    val prefix: String, val suffix: String,
    val openFiles: List<OpenFileSnippet>, val multiline: Boolean, val maxLines: Int,
)
data class OpenFileSnippet(val path: String, val languageId: String, val content: String)

data class ClaudeConfig(
    val claudePath: String, val model: String, val fallbackModel: String, val effort: String,
    val thinkingEnabled: Boolean, val thinkingBudgetTokens: Int, val requestTimeoutMs: Int,
    val persistentProcess: Boolean, val customInstructions: String,
)

sealed interface CompletionResult {
    data class Success(val text: String) : CompletionResult
    data object Empty : CompletionResult
    data class Failure(val kind: FailureKind, val message: String) : CompletionResult
}
enum class FailureKind { NotLoggedIn, CliNotFound, InvalidModel, Timeout, Other }

interface CompletionBackend {            // cancellable: coroutine cancellation must interrupt the CLI
    suspend fun complete(context: CompletionContext, config: ClaudeConfig): CompletionResult
    fun shutdown()
}
```
Classes: `PromptBuilder` (system prompt + user message), `ClaudeCliLocator` (resolves binary),
`ClaudeCommandBuilder` (args + env, pure), `StreamJsonParser` (line -> event), `OneShotClaudeBackend`,
`PersistentClaudeBackend`, `ClaudeCliBackend` (facade choosing mode, restart on config change,
fallback), `completion/CompletionPostProcessor` (strip fences, overlap trimming, single-line
truncation, maxLines, trailing whitespace rules), `completion/CompletionCache` (LRU),
`completion/TriggerRules` (mid-line rule + multiline decision from prefix/suffix strings).
Use `kotlinx.serialization`? NO - not bundled guaranteed. Use the platform-bundled
`com.fasterxml.jackson` ? NO. Write a minimal JSON reader/escaper yourself OR use
`com.google.gson` which is bundled in the IntelliJ platform (verify it's on the compile classpath).

### IDE integration (agent B) - `dev.ksurdy.claudeautocomplete` (+ `.settings`, `.ui`, `.actions`)
`ClaudeInlineCompletionProvider : DebouncedInlineCompletionProvider`, `ContextCollector` (readAction,
prefix/suffix caps, open tabs via FileEditorManager + FileDocumentManager.getCachedDocument),
`ClaudeAutocompleteSettings` (SimplePersistentStateComponent, app service, `toClaudeConfig()`),
`ClaudeAutocompleteConfigurable` (BoundSearchableConfigurable, UI DSL v2, "Test connection" button
that runs one completion and shows latency/result), `StatusService` (app service holding
state + listeners), status bar widget factory, `ToggleAction`, `TriggerAction` (Alt+\ ),
`Notifier`, app service `BackendService` owning a `ClaudeCliBackend` (disposed on app shutdown),
`plugin.xml`, plugin icon `META-INF/pluginIcon.svg`. Agent B calls agent A's interfaces exactly as
specified above (`ClaudeCliBackend()` no-arg constructor implementing `CompletionBackend`;
`TriggerRules.shouldTrigger(prefix, suffix): Boolean`, `TriggerRules.isMultiline(prefix, suffix, mode: String): Boolean`;
`CompletionPostProcessor.process(raw: String, context: CompletionContext): String`;
`CompletionCache(capacity: Int)` with `get(key: String): String?` / `put(key, value)`, and
`CompletionCache.key(context, model): String`).
