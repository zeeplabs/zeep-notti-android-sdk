# Package Foundation Specification

## Problem Statement

The repo is empty. Before any Kotlin is ported (`core-port`), the SDK needs a Gradle library that native apps can consume as an AAR and that `react-native-notti` can depend on in M2, with the namespace fixed by AD-003, a single version source, a public-API guard and CI that builds and tests on every PR. Getting the coordinates, namespace or module layout wrong now is inherited by every feature and by the M2 migration.

## Goals

- [ ] `implementation("com.zeeplabs:notti-android:<version>")` resolves from a local Maven repository and an app module compiles against it, with no React Native on the classpath.
- [ ] One CI workflow on every PR builds the AAR, runs the unit tests, lint and the API check, and is green on an empty-but-real skeleton.
- [ ] The SDK version has one source of truth that the code (`sdk_version` field), the publication and git tags agree on.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Any SDK behavior (registration, FCM, In-App) | `core-port`, `public-api`, `push-integration`, `in-app-messages` |
| Publishing to Maven Central, creating the GitHub remote | Outward action, `example-and-release`, after explicit confirmation (AD-004) |
| Example app content | `example-and-release` (this feature only adds a compiling placeholder module) |
| Huawei/HMS, Wear OS, TV | Not in PROJECT scope |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Namespace / package | `com.zeeplabs.notti` | AD-003 | y (Julio, 2026-10-28) |
| Coordinates | `com.zeeplabs:notti-android` | AD-003 | y (Julio, 2026-10-28) |
| Repository | Maven Central | AD-004 | y (Julio, 2026-10-28) |
| Module layout | `:notti` (library) + `:example` (app placeholder) | One AAR like RN; no FCM-free "core" split needed (Android has no NSE equivalent) | n - proposed in `design.md` |
| `minSdk` / `compileSdk` / JVM | 24 / 36 / 17 | RN 0.8.0 `android/build.gradle` | y (RN baseline) |
| Kotlin / AGP | AGP 9.2 + Gradle 9.4.1 + Kotlin 2.2 (`apiVersion 2.0`), fallback AGP 8.13 + Kotlin 2.0.21 | Spike S1 decides (design.md) | y (Julio, 2026-10-28; spike-gated) |
| Firebase dependency scope | `api` for `firebase-messaging` | `NottiBridge.onMessageReceived(RemoteMessage)` exposes a Firebase type in the public API | n - proposed in `design.md` |
| Lint | Spotless + ktlint (official style) + Android Lint `warningsAsErrors` on the library | Kotlin counterpart of SwiftLint `--strict`; no detekt in M1 | n - proposed in `design.md` |
| Public API guard | KGP built-in ABI validation, fallback BCV | AD-010, design.md | n (proposed) |
| License | MIT | Same as RN and iOS repos | y (Julio, 2026-10-28) |
| CI | GitHub Actions, `ubuntu-latest`, JDK 17 | No emulator needed for JVM/Robolectric tests | y (technical) |

---

## User Stories

### P1: Consume the SDK as a Gradle dependency ⭐ MVP

**User Story**: As an Android developer, I want to add one Gradle dependency, so that I can integrate Notti without React Native.

**Acceptance Criteria**:

1. WHEN the project is built THEN the library module SHALL declare `namespace = "com.zeeplabs.notti"`, `minSdk = 24`, `compileSdk = 36`, Java/Kotlin target 17, and produce a release AAR.
2. WHEN the library is published to a local Maven repository (`publishReleasePublicationToMavenLocal` or a build-dir repo) THEN the POM SHALL have groupId `com.zeeplabs`, artifactId `notti-android`, the SDK version, MIT license, SCM and developer entries, and runtime dependencies with no `com.facebook.react` artifact.
3. WHEN `:example` depends on the library through the published coordinates (not a project dependency) THEN it SHALL compile.
4. WHEN any source file is scanned THEN no file SHALL declare a package under `com.notti` (exactly; `com.zeeplabs.notti` is allowed). A CI check fails otherwise (AD-003, constraint on FQN collisions).
5. WHEN the AAR is inspected THEN it SHALL contain `consumer-rules.pro` (R8 keep rules for the manifest-declared components, empty-but-present now) and a library manifest with no `<application>` attributes that would override the host's (`allowBackup`, `label`, `icon`, `theme`).

**Independent Test**: build + local publish + `:example:assembleDebug` against the published coordinates; `unzip -l` on the AAR; the package scan script.

---

### P1: Single version source ⭐ MVP

**User Story**: As the maintainer, I want one place that defines the SDK version, so that `sdk_version` sent to the backend, the Maven version and the git tag never drift.

**Acceptance Criteria**:

1. WHEN the version is read THEN `NottiSdkVersion.CURRENT` (Kotlin `const val`, `internal` until `public-api`) SHALL be the only literal; Gradle reads the publication version from the same source (or a check fails on drift).
2. WHEN `scripts/check-version.sh` runs in CI THEN it SHALL fail if the Kotlin constant and the Gradle publication version differ, and on a tag build if the tag (`X.Y.Z`, same as iOS) differs.
3. WHEN the check runs with a missing input file THEN it SHALL fail, not pass (lesson carried from iOS L-002).
4. WHEN a unit test reads `NottiSdkVersion.CURRENT` THEN it SHALL match semver `MAJOR.MINOR.PATCH(-prerelease)?`.

**Independent Test**: change one side only, run the script, see it fail; restore, see it pass.

---

### P1: CI gate ⭐ MVP

**User Story**: As the maintainer, I want every PR to build, test and lint the SDK, so that regressions from the port are caught before merge.

**Acceptance Criteria**:

1. WHEN a PR is opened or `main` is pushed THEN `.github/workflows/ci.yml` SHALL run: lint (ktlint + Android Lint), `:notti:testReleaseUnitTest` (or debug, decided at Design), `:notti:apiCheck`, `:notti:assembleRelease`, local publish + `:example:assembleDebug`, `scripts/check-version.sh`, and the package scan.
2. WHEN any step fails THEN the job SHALL fail (no `continue-on-error`).
3. WHEN Android Lint runs on the library THEN warnings SHALL be errors on the library module (the job that enforces it is named in the workflow, iOS L-003).
4. WHEN the test task runs THEN Robolectric and the real `org.json` SHALL be on the test classpath (RN test infra), proved by one smoke test that uses each.
5. WHEN Gradle runs in CI THEN the Gradle wrapper SHALL be validated (`gradle/actions/wrapper-validation`) and dependencies cached.

**Independent Test**: a PR with a deliberately failing test, lint error and API change each turns CI red.

---

### P2: Public API guard

**User Story**: As the maintainer, I want any change to the public surface to be explicit in review, so that the API RN depends on in M2 never changes by accident.

**Acceptance Criteria**:

1. WHEN `:notti:apiCheck` runs THEN it SHALL compare against `notti/api/notti.api` and fail on any difference.
2. WHEN the skeleton is built THEN the dump SHALL list only the intentionally public symbols (none or the version accessor, decided at Design).
3. WHEN a contributor changes public API THEN `AGENTS.md` SHALL tell them to run `apiDump` and commit the diff in the same commit.

**Independent Test**: make an `internal` class `public`; `apiCheck` fails.

---

### P2: Agent and contributor rules

**User Story**: As any contributor or coding agent, I want one rules file, so that the port rule, privacy rules and commands are not re-explained per session.

**Acceptance Criteria**:

1. WHEN the repo is opened THEN `AGENTS.md` SHALL hold the rules (source of truth `.specs/`, port rule AD-002, package rule AD-003, logging/LGPD AD-011, public API AD-010, commands, conventional commits, no AI attribution) and `CLAUDE.md` SHALL only point to it.
2. WHEN `.specs/codebase/TESTING.md` is created THEN it SHALL list the test commands, the test infrastructure and the coverage matrix to be filled by later features.

**Independent Test**: review.

---

## Edge Cases

- WHEN the host app also applies `com.google.gms.google-services` THEN nothing in this library SHALL require its own `google-services.json` (the library never applies that plugin).
- WHEN the host uses R8 full mode THEN the consumer rules SHALL keep the manifest-declared classes (filled in `push-integration`; this feature proves the file ships in the AAR).
- WHEN the host has `minSdk` above 24 THEN the manifest merge SHALL succeed with no `tools:overrideLibrary`.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| PKG-01 | P1 Gradle dependency: module config and AAR | Tasks | Mapped |
| PKG-02 | P1 Gradle dependency: POM coordinates and metadata, no React Native | Tasks | Mapped |
| PKG-03 | P1 Gradle dependency: example consumes published coordinates | Tasks | Mapped |
| PKG-04 | P1 Gradle dependency: no `com.notti` package (CI check) | Tasks | Mapped |
| PKG-05 | P1 Gradle dependency: consumer rules and neutral library manifest | Tasks | Mapped |
| PKG-06 | P1 Version: single Kotlin constant | Tasks | Mapped |
| PKG-07 | P1 Version: drift check script, fails on missing input | Tasks | Mapped |
| PKG-08 | P1 Version: semver unit test | Tasks | Mapped |
| PKG-09 | P1 CI: workflow steps | Tasks | Mapped |
| PKG-10 | P1 CI: no swallowed failures, Lint warnings as errors on the library | Tasks | Mapped |
| PKG-11 | P1 CI: Robolectric + org.json smoke tests | Tasks | Mapped |
| PKG-12 | P1 CI: wrapper validation and cache | Tasks | Mapped |
| PKG-13 | P2 API guard: `apiCheck` against committed dump | Tasks | Mapped |
| PKG-14 | P2 Rules: `AGENTS.md`, `CLAUDE.md`, `TESTING.md` | Tasks | Mapped |

**Coverage:** 14 total, 14 mapped to tasks (`tasks.md`), 0 unmapped.

---

## Success Criteria

- [ ] A clean checkout runs `./gradlew build` and the CI steps locally with no network beyond dependency resolution.
- [ ] The example module compiles against `com.zeeplabs:notti-android` from a local repository.
- [ ] CI turns red on: a failing test, a lint error, an API change without dump, a version drift, a `com.notti` package.
