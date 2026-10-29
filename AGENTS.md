# Notti Android SDK (zeep-notti-android-sdk): agent and contributor rules

Native Android SDK for Zeep Notti push notifications and In-App Messages. Kotlin, `minSdk 24`, `compileSdk 36`, JVM 17. One library module (`:notti`, namespace `com.zeeplabs.notti`) published as `com.zeeplabs:notti-android` on Maven Central, plus `:example`.

These rules apply to every contributor and every coding agent (Claude Code, Codex, Cursor, Copilot, etc.). This file is the single source of truth; `CLAUDE.md` only points here.

## Source of truth

- Plans and decisions live in `.specs/` (`tlc-spec-driven` workflow): `project/PROJECT.md`, `project/ROADMAP.md`, `project/STATE.md` (decisions AD-xxx, blockers, todos), `features/<feature>/{spec,design,tasks}.md`, `codebase/TESTING.md`.
- Read `STATE.md` and the current feature's `tasks.md` before starting work. Any behavior change needs a spec in this repo first (AD-001).
- Record new architecture decisions as AD entries in `STATE.md`.
- Sister repos: `../zeep-notti-react-native` (behavior source, tag `v0.8.0`) and `../zeep-notti-ios-sdk` (process and cross-SDK decisions).

## Layout

```
notti/                     the SDK (Android library)
  src/main/java/com/zeeplabs/notti/
  src/test/java/com/zeeplabs/notti/   JUnit 4, Robolectric where needed
  api/notti.api            public ABI dump (AD-010)
  consumer-rules.pro       R8 rules shipped in the AAR
example/                   sample app; CI builds it against the published AAR
scripts/                   check-version.sh, check-package-name.sh; self-tests in scripts/tests/
gradle/libs.versions.toml  every version of the build
```

## Commands

Use JDK 17 (CI uses Temurin 17). On macOS with Homebrew: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`.

```sh
# unit tests (one class: --tests 'com.zeeplabs.notti.<Class>')
./gradlew :notti:testDebugUnitTest
# style and lint (lint warnings are errors on the library)
./gradlew spotlessCheck :notti:lintRelease      # spotlessApply fixes style
# public API: check, and update the dump after an intended change
./gradlew :notti:apiCheck
./gradlew :notti:apiDump
# build, publish to build/repo, build the example against the published AAR (separate invocation)
./gradlew :notti:assembleRelease :notti:publishAllPublicationsToBuildRepository
./gradlew -Pnotti.usePublished=true :example:assembleDebug
# guards and their self-tests
scripts/check-version.sh && scripts/check-package-name.sh && scripts/tests/run.sh
```

## Rules

### M1 is a port, not a rewrite (AD-002)

Until 0.1.0 ships, code ported from `react-native-notti` 0.8.0 (`../zeep-notti-react-native/android` at tag `v0.8.0`, `291d5a7`) stays behavior-identical:

- Copy the RN file, then change only what the task lists (package, RN coupling, logger, visibility). Start the file with a provenance comment naming its RN source.
- Keep the single-thread executor, the blocking OkHttp client with retry/backoff and every `notti_prefs` key exactly as in RN.
- Port RN tests 1:1 (JUnit 4, Robolectric where RN uses it, same `@Config(sdk = …)`).
- No coroutines/Flow rewrite of ported code in M1.
- Ported files are re-indented to 4 spaces; check parity with `diff -w` against RN, ignoring the `package`/import lines (AD-009). Lint findings RN already has are suppressed per file, not fixed.

### Package (AD-003)

Everything lives under `com.zeeplabs.notti`. Never declare or import `com.notti`: that is RN's package, and equal fully qualified names on one classpath break the host build. `scripts/check-package-name.sh` enforces it in CI. The `notti_prefs` file name and keys stay RN's.

### Privacy and logging (LGPD, AD-011)

- Log only through `NottiLogger` (added by `public-api`; until then ported code keeps RN's calls and the port task reroutes them). Default level error.
- Never put email, phone, push token, external user id, country, tag values or URLs in a log line; log errors by class/code, not by message.
- New test fixtures use synthetic values (`example.invalid`, `fixture`); ported RN tests keep theirs.
- A feature that collects a new data type updates the README data-collection and Play Data safety section in the same change.

### Public API (AD-010)

Ported internals are `internal`. Every change to public API updates `notti/api/notti.api` in the same commit: run `./gradlew :notti:apiDump`, review the diff, commit it. `apiCheck` fails CI on drift. The dump is built by BCV task types wired by hand in `notti/build.gradle.kts`, because KGP ABI validation and BCV auto-wiring do not work with AGP built-in Kotlin (AD-012); re-check that wiring when upgrading BCV, Kotlin or AGP.

### Build and dependencies (AD-012, AD-013)

- Every version lives in `gradle/libs.versions.toml`. Toolchain: Gradle 9.4.1, AGP 9.2.1 with built-in Kotlin, Kotlin compiler 2.2.21 (pinned through the root `buildscript` classpath), `languageVersion`/`apiVersion` 2.0.
- Runtime dependencies are pinned to what RN 0.8.0 resolves, so the SDK does not force upgrades on hosts. Raise one only when code needs it, and say why in the commit.
- `firebase-messaging` is `api`; the rest are `implementation`. The library never applies the Google Services plugin.

### Version

`NottiSdkVersion.CURRENT` (`notti/src/main/java/com/zeeplabs/notti/NottiSdkVersion.kt`) is the only place the version is defined; the Gradle build reads it for the publication. Tags are `X.Y.Z` (no `v`). `scripts/check-version.sh` checks the CHANGELOG top released section and, on tag builds, the tag, a dated section and the README banner.

### Code conventions

- 4-space indentation, ktlint official style (`spotlessCheck` must pass).
- Every code change ships with its tests in the same commit (coverage matrix in `.specs/codebase/TESTING.md`). Test counts must not drop.

### Git and CI

- Conventional commits (`feat(scope):`, `fix:`, `test:`, `build:`, `ci:`, `docs(specs):`, `chore:`), one task per commit.
- Do not add AI co-authorship or attribution lines to commits or PRs.
- CI (`.github/workflows/ci.yml`) runs on pull requests, pushes to `main` and tags. Actions are pinned by commit SHA with the version in a comment.
- Creating the remote, pushing, opening PRs and publishing to Maven Central are done only with the maintainer's explicit OK.
