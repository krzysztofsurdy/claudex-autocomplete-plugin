# Contributing to Claudex Autocomplete

Thanks for helping improve the plugin. This guide covers how to report problems, set up a build, run the tests and submit changes.

## Ways to contribute

### Bug reports

Open an issue and include:

- IDE name and build number (Help | About) and the plugin version.
- Provider in use (Claude Code or Codex) and the CLI version (`claude --version` or `codex --version`).
- Steps to reproduce, what you expected and what happened.
- A relevant excerpt of `idea.log` (Help | Show Log in Finder/Explorer/Files). Remove anything private before pasting.

### Feature ideas

Open an issue to discuss the idea before writing code, so effort is not spent on something that does not fit the plugin.

## Development setup

You need a local PhpStorm 2025.3 or newer. Its bundled JetBrains Runtime is used as the JDK:

```
export JAVA_HOME=/path/to/PhpStorm.app/Contents/jbr/Contents/Home
```

Point the build at the IDE with a Gradle property, either per command:

```
./gradlew buildPlugin -PlocalIdePath=/path/to/PhpStorm.app
```

or once in `~/.gradle/gradle.properties`:

```
localIdePath=/path/to/PhpStorm.app
```

Start a sandbox IDE with the plugin loaded:

```
./gradlew runIde
```

## Tests

```
./gradlew test
```

This runs the unit tests and the headless IDE platform tests (started through `IdeTestsRunnerTest`).

Integration tests that talk to the real CLIs are skipped by default. They need a logged-in CLI and consume your subscription quota:

```
CLAUDE_IT=1 ./gradlew test
CODEX_IT=1 ./gradlew test
```

Write a failing test first, then the implementation. Keep pure logic in `completion/` and `backend/` so it can be unit tested without IDE APIs.

## Project layout

Sources live under `src/main/kotlin/dev/ksurdy/claudeautocomplete`.

| Package | Contents |
|---|---|
| `backend/` | CLI processes for Claude Code and Codex, command builders, parsers, prompt building |
| `completion/` | Pure post-processing, trigger rules and cache logic |
| `php/` | PHP class outline and imported-class context |
| `actions/`, `ui/` | Editor actions and the status bar widget |
| top level | IDE integration: inline completion provider, settings, context collection, status service |

Tests mirror this layout under `src/test/kotlin`; platform tests are in `ide/`.

## Code style

- Kotlin official code style.
- No explanatory comments or docblocks. Name things clearly instead.
- No new dependencies without discussing them in an issue first.
- Never block the EDT. Run CLI work off the UI thread.
- Read PSI and documents inside `readAction`.

## Commits and pull requests

Use Conventional Commits: `type(scope): description`.

- Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.
- Scope is optional, for example `backend`, `ide`, `build`.
- Keep the subject line under 72 characters.
- Examples from the history: `feat(backend): add Codex CLI provider`, `chore(build): disable searchable options generation`.

Pull requests:

- One logical change per PR.
- `./gradlew test buildPlugin` must pass.
- Contributions are accepted under the project's GNU GPL v3.0 license (see `LICENSE`).
- Update `README.md`, and the description and change notes in `src/main/resources/META-INF/plugin.xml`, when user-facing behaviour changes.
