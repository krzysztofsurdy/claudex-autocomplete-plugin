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

You need JDK 21. By default Gradle downloads PhpStorm 2025.3 as the build target. To use a local PhpStorm 2025.3 or newer instead, set `localIdePath` (optional). Its bundled JetBrains Runtime works as the JDK:

```
export JAVA_HOME=/path/to/PhpStorm.app/Contents/jbr/Contents/Home
```

Optionally point the build at the local IDE with a Gradle property, either per command:

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
- Add a bullet under `## [Unreleased]` in `CHANGELOG.md` for every user-facing change, grouped as Added, Changed, Deprecated, Removed, Fixed or Security.
- Update `README.md`, and the description and change notes in `src/main/resources/META-INF/plugin.xml`, when user-facing behaviour changes.

## Branch protection

`main` is protected. All changes go through pull requests, and the CI check must pass before merging.

## Releasing

Releases are driven by a version tag. The `Release` workflow tests, verifies, signs (when configured), creates the GitHub Release and publishes to JetBrains Marketplace.

1. In `CHANGELOG.md`, move the entries under `## [Unreleased]` into a new `## [X.Y.Z] - YYYY-MM-DD` section and update the link references at the bottom.
2. Add a matching `<h4>X.Y.Z</h4>` entry at the top of `<change-notes>` in `src/main/resources/META-INF/plugin.xml`, newest version first. The workflow fails early if either the change-notes entry or the changelog section for the released version is missing.
3. Merge the change to `main` through a pull request.
4. Tag the merge commit and push only that tag (never `git push --tags`):

```
git tag vX.Y.Z
git push origin vX.Y.Z
```

The release waits for approval in the `marketplace` environment before it publishes.

The plugin version comes from the tag (`-PpluginVersion`); `build.gradle.kts` falls back to `1.0.0` for local builds.

A suffixed tag such as `v0.2.0-beta.1` is published as a GitHub pre-release and to the Marketplace channel named by the first suffix part (`beta`). Change notes and the changelog are checked against the core version (`0.2.0`). Plain tags go to the default channel.

The release job runs in the `marketplace` GitHub environment, where required reviewers can be configured for a manual approval step.

Secrets (repository or `marketplace` environment):

| Secret | Required | Source |
|---|---|---|
| `PUBLISH_TOKEN` | yes | Marketplace > My Profile > My Tokens |
| `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` | no | [Plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) |

Signing is skipped automatically when the signing secrets are absent, and the unsigned zip is published.
