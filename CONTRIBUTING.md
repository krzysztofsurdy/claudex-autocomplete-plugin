# Contributing

## Bug reports

Open an issue with:

- IDE name and build (Help | About), plugin version.
- Provider and CLI version (`claude --version` or `codex --version`).
- Steps to reproduce, expected vs actual.
- Relevant part of `idea.log` (Help | Show Log). Remove anything private.

For feature ideas open an issue first.

## Setup

JDK 21. Gradle downloads PhpStorm 2025.3 by default. To use a local PhpStorm 2025.3+ instead, set `localIdePath` - its bundled runtime works as the JDK:

```
export JAVA_HOME=/path/to/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew buildPlugin -PlocalIdePath=/path/to/PhpStorm.app
```

or once in `~/.gradle/gradle.properties`:

```
localIdePath=/path/to/PhpStorm.app
```

Without PhpStorm, install a JDK 21 with Homebrew instead:

```
brew install openjdk@21
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
```

Sandbox IDE with the plugin: `./gradlew runIde`

## Tests

```
./gradlew test
```

Runs unit tests and headless IDE platform tests (through `IdeTestsRunnerTest`). Integration tests with the real CLIs are skipped by default, they need a logged-in CLI and use your quota:

```
CLAUDE_IT=1 ./gradlew test
CODEX_IT=1 ./gradlew test
```

UI tests start a real PhpStorm with the built plugin (IntelliJ Starter + Driver), open the project in `src/uiTest/resources/php-project`, type characters and check the accepted suggestion. They call the real Claude CLI, so they are opt-in too. The first run downloads PhpStorm:

```
CLAUDE_IT=1 ./gradlew uiTest -PuiTestIdeVersion=2025.3
```

New UI tests must drive the IDE only through in-IDE actions and APIs: `invokeAction`, the platform's typing action through the Driver and `moveCaretToOffset`. Never use OS-level keyboard or mouse input, because it goes to whatever app has focus on the developer's machine. They also never capture the screen, and the shared setup enforces it. Start every IDE with `GuardedIde.start(context)` and stop it with `GuardedIde.close()`, never with `runIdeWithDriver` and `closeIdeAndWait` directly. Five layers keep OS capture off:

- `Driver.takeScreenshot(name)` is a full-screen `java.awt.Robot` capture inside the IDE (`TakeScreenshotCommandKt.takeFullScreenshot`, written as `log/screenshots/<name>/full_screen.png`), and on macOS it shows whatever is on top of the IDE. The Starter calls it from `closeIdeAndWait(takeScreenshot = true)` and, unconditionally, from `DriverWithDetailedLogging` whenever a `driver.withContext`, `withReadAction` or `withWriteAction` block throws (the `driverError` screenshot). `DriverScreenshotBlock` swaps the wrapper's inner driver for a proxy that refuses `takeScreenshot` and counts the attempts, so a failing driver call surfaces its error without a capture. Never call `Driver.takeScreenshot` yourself.
- The Starter always adds `-Dide.performance.screenshot=heartbeat`, which makes the IDE's performance plugin screenshot its windows every minute (it paints the windows off-screen, not an OS capture, but it is switched off anyway). `GuardedIde.start` removes that property from the run's VM options (the plugin only starts the job when the property is set at all).
- When a run hits its timeout the Starter kills the IDE and first runs an external `java -jar TakeScreenshot`, a full-screen `java.awt.Robot` capture, and it has no option to turn that off. `CaptureTripwire.disarmKillScreenshot` replaces that tool jar in the Starter cache with an inert one before every run. Runs also get an explicit timeout and close the IDE in `finally`/`@AfterAll`.
- `ScreenshotGuard.enforce` asks the running IDE whether the property is unset and cancels any screenshot job it finds, and the run aborts if it cannot tell or finds one.
- `GuardedIde.close` is a tripwire: after the IDE exits it deletes, without opening, any `*screenshot*`, `heartbeat*`, `*.jpg`, `*.jpeg` or `*.png` under the run's test home (`out/ide-tests/tests/**`, which includes `log`) and fails the test. With the other layers in place it never fires.

`CLAUDEX_SOAK=1 ./gradlew uiTest -PuiTestIdeVersion=2025.3 --tests '*OsCaptureFreeUiTest*'` keeps an IDE idle for 80 seconds, past the first heartbeat, and checks that nothing was captured or logged. A second test there fails a driver call on purpose and checks that the screenshot requests were blocked and no capture or log line appeared. Neither calls the Claude CLI.

Failing test first, then the code. Keep pure logic in `completion/`, `backend/`, `context/` and `nextedit/`.

Test pitfalls:

- Gradle's build cache can replay old test results. Verify with `./gradlew test --rerun --no-build-cache`.
- Register new headless IDE test classes in `IdeTestsRunnerTest`, or they never run.
- An IDE test that accepts a suggestion must call `CompletionRequests.getInstance().cancel()` in tearDown before it restores the engine. Otherwise a background prefetch can start the real CLI.
- No wall-clock waits and no pixel or rendering checks in fixture tests, CI runs on headless Linux. Inject clocks, drive timers by hand and compare icons structurally.

## Layout

Sources are in `src/main/kotlin/dev/ksurdy/claudeautocomplete`. Tests mirror it under `src/test/kotlin`, platform tests in `ide/`.

| Package | Contents |
|---|---|
| `backend/` | CLI processes, command builders, parsers, prompt |
| `completion/` | Post-processing, trigger rules, cache |
| `context/` | Related snippet ranking from open tabs: tokenizer, stop words, window scorer, selector |
| `nextedit/` | Next edit suggestions: edit history, prompt section, reply parser, target choice, timing policy, rejection memory, Tab handlers, preview |
| `php/` | PHP class outlines and imported-class context |
| `alternatives/` | Next and Previous suggestion handlers, alternative requests |
| `settings/` | Settings page group builders, provider choice |
| `actions/`, `ui/` | Editor actions, status bar widget |
| top level | Inline completion provider, settings, context collection, status service, request service (`CompletionRequests`, `RunningCompletion`: in-flight reuse and prefetch), `EditorGates` |

## Code style

- Kotlin official style.
- No explanatory comments or docblocks, name things clearly.
- No new dependencies without an issue first.
- Never block the EDT, run CLI work off the UI thread.
- Read PSI and documents inside `readAction`.

## Commits and PRs

Conventional Commits: `type(scope): description`.

- Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.
- Scope is optional, e.g. `backend`, `ide`, `build`.
- Subject under 72 characters.

PRs:

- One change per PR.
- `./gradlew test buildPlugin` must pass.
- Contributions are under GPL v3.0 (see `LICENSE`).
- Add a line under `## [Unreleased]` in `CHANGELOG.md` for every user-facing change.
- Update `README.md` and the description and change notes in `src/main/resources/META-INF/plugin.xml` when user-facing behaviour changes.

## Branch protection

`main` is protected. Changes go through PRs and CI must pass.

## Releasing

A version tag drives the `Release` workflow: test, verify, sign (if configured), GitHub Release, publish to JetBrains Marketplace.

1. In `CHANGELOG.md` move `## [Unreleased]` entries into `## [X.Y.Z] - YYYY-MM-DD` and update the link refs at the bottom.
2. Add `<h4>X.Y.Z</h4>` at the top of `<change-notes>` in `src/main/resources/META-INF/plugin.xml`. The workflow fails early if this or the changelog section is missing.
3. Merge to `main` through a PR.
4. Tag the merge commit and push only that tag (never `git push --tags`):

```
git tag vX.Y.Z
git push origin vX.Y.Z
```

The release waits for approval in the `marketplace` environment. Version comes from the tag (`-PpluginVersion`), local builds fall back to `1.0.0`.

A tag like `v0.2.0-beta.1` is a GitHub pre-release and goes to the Marketplace channel `beta`. Checks use the core version (`0.2.0`). Plain tags go to the default channel.

Secrets (repository or `marketplace` environment):

| Secret | Required | Source |
|---|---|---|
| `PUBLISH_TOKEN` | yes | Marketplace > My Profile > My Tokens |
| `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` | no | [Plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) |

Without the signing secrets the unsigned zip is published.
