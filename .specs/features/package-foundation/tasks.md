# Package Foundation Tasks

**Design**: `.specs/features/package-foundation/design.md`
**Testing**: `.specs/codebase/TESTING.md` (created by T13; until then the matrix below is authoritative)
**Status**: In Progress (T1 done 2026-10-28, report in STATE AD-012/AD-013)

---

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: activate it by name and follow its Execute flow. Project rules are in `AGENTS.md` (from T13 on; before that, `.specs/project/STATE.md`). One task per commit, conventional commits, no AI attribution. Anything outward-facing (creating the GitHub remote, pushing, opening a PR, publishing) needs Julio's explicit OK at that moment.

---

## Test Coverage Matrix

> Greenfield: generated from the approved design.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| `NottiSdkVersion` | unit | semver format | `notti/src/test/java/com/zeeplabs/notti/*Test.kt` | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` |
| Test infrastructure (Robolectric, org.json) | unit (smoke) | one test each, fails without the dependency | same | same |
| Gradle build, publication, ABI, lint | none (build) + inspection | build passes; negative proof for each guard | - | Build gate |
| Scripts (`scripts/*.sh`) | script self-test | pass fixture + each failure fixture | `scripts/tests/*-test.sh` | `scripts/tests/run.sh` |
| CI workflow | none | `actionlint` clean; red and green run once a remote exists | - | `actionlint` |
| Repo hygiene / docs | none | - | - | - |

## Parallelism Assessment

| Test Type | Parallel-Safe | Why |
| --- | --- | --- |
| Gradle (unit, build) | No | one checkout, one Gradle daemon and build dir |
| Script self-tests | Yes | temp fixture dirs, no Gradle |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | unit-test tasks | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` |
| Build | Gradle config tasks | `./gradlew :notti:assembleRelease` (+ task-specific command) |
| Scripts | script tasks | `scripts/tests/run.sh` |
| Full | after T12 | `./gradlew spotlessCheck :notti:lintRelease :notti:testDebugUnitTest :notti:apiCheck :notti:assembleRelease :notti:publishAllPublicationsToBuildRepository && ./gradlew -Pnotti.usePublished=true :example:assembleDebug && scripts/check-version.sh && scripts/check-package-name.sh && scripts/tests/run.sh` |

---

## Execution Plan

### Phase 1: Risk first

```
T1 (spike S1, throwaway)
```

### Phase 2: Repo and Gradle skeleton (sequential)

```
T1 ──→ T2 ──→ T3 ──→ T4 ──→ T5 ──→ T6 ──→ T7 ──→ T8 ──→ T9
```

### Phase 3: Scripts (parallel with Phase 2 after T2)

```
T2 → T10 [P]
T2 → T11 [P]
```

### Phase 4: Integration (sequential)

```
T9 → T12
T10 → T12
T11 → T12
T12 → T13
```

---

## Task Breakdown

### T1: Spike S1 - prove the toolchain

**What**: In a throwaway directory in the scratchpad (not this repo), build a minimal library + app with the primary toolchain and run the seven checks of design.md "Spike S1".
**Where**: scratchpad only; results in `.specs/project/STATE.md` (amend AD-012) and a pinned-versions list for T3
**Depends on**: None
**Reuses**: design.md Spike S1; RN `example/android` (scratch copy only, never modified in place)
**Requirement**: PKG-01, PKG-02, PKG-05, PKG-13 (de-risk)

**Done when**:

- [x] Checks 1-7 each recorded as pass/fail with the command used
- [x] Exact versions pinned: AGP, Gradle, Kotlin, vanniktech plugin, Spotless, ktlint, ABI tool (+ task names), and the RN example's resolved `firebase-messaging`, `lifecycle-process`, `androidx.core`, `androidx.activity`, `androidx.annotation`, `okhttp`
- [x] AD-012 amended: primary confirmed, or fallback chosen with the failing check named
- [x] Nothing committed to the RN repo

**Tests**: none
**Gate**: spike report reviewed by Julio (approved 2026-10-28)
**Commit**: `docs(specs): record spike S1 toolchain results`

---

### T2: Repo hygiene files

**What**: `git init` (local), `.gitignore` (Gradle, Android Studio, `local.properties`, `google-services.json`, `build/`), `.gitattributes` (`gradlew` LF, `*.bat` CRLF), `.editorconfig` (4 spaces, ktlint official), `LICENSE` (MIT), `CHANGELOG.md` (`## [Unreleased]` only), `README.md` stub (status: pre-release).
**Where**: repo root
**Depends on**: T1
**Requirement**: PKG-14 (partial)

**Done when**:

- [x] Files present; `google-services.json` and `local.properties` ignored
- [x] First commit on `main`; existing `.specs/` included

**Tests**: none
**Gate**: `git status` clean after commit
**Commit**: `chore: initialize repository`

---

### T3: Gradle skeleton

**What**: Wrapper (version from T1), `settings.gradle.kts` (`:notti`, `:example`, repositories, `notti.usePublished` switch read here), root `build.gradle.kts` (plugins `apply false`), `gradle.properties` (`android.useAndroidX=true`, POM metadata, JVM args), `gradle/libs.versions.toml` with every version from T1.
**Where**: repo root, `gradle/`
**Depends on**: T2
**Requirement**: PKG-01, PKG-12 (wrapper)

**Done when**:

- [x] `./gradlew help` succeeds with an empty `:notti` and `:example` directory stub
- [x] No version literal outside `libs.versions.toml` and the wrapper properties

**Tests**: none
**Gate**: `./gradlew help`
**Commit**: `build: add Gradle wrapper, settings and version catalog`

---

### T4: `:notti` module, version constant and semver test

**What**: `notti/build.gradle.kts` (namespace, SDKs, Java 17, Kotlin `apiVersion`/`languageVersion` 2.0, `consumerProguardFiles`, unit-test resources, `buildConfig = false`, version read from `NottiSdkVersion.kt` with the 0-or-2+-matches failure), `AndroidManifest.xml` (empty), `consumer-rules.pro` (header comment), `NottiSdkVersion.kt` (`internal`, `"0.1.0"`), runtime and test dependencies from the catalog, `NottiSdkVersionTest`.
**Where**: `notti/`
**Depends on**: T3
**Requirement**: PKG-01, PKG-05, PKG-06, PKG-08

**Done when**:

- [x] `:notti:assembleRelease` produces the AAR; `unzip -l` shows `AndroidManifest.xml` and `proguard.txt`
- [x] Removing/duplicating the constant line fails configuration with a clear message (checked by hand, noted in the commit body)
- [x] `NottiSdkVersionTest` passes

**Tests**: unit
**Gate**: Quick + Build
**Commit**: `feat(build): add notti library module and version constant`

---

### T5: Test infrastructure smoke tests

**What**: `RobolectricSmokeTest` (`@Config(sdk = [34])`, `Bundle` round trip) and `OrgJsonSmokeTest`.
**Where**: `notti/src/test/java/com/zeeplabs/notti/`
**Depends on**: T4
**Requirement**: PKG-11

**Done when**:

- [x] Both pass; temporarily removing each dependency makes its test fail (noted in the commit body)

**Tests**: unit
**Gate**: Quick
**Commit**: `test: add Robolectric and org.json smoke tests`

---

### T6: Kotlin style and Android Lint gates

**What**: Spotless + ktlint (official style) on `*.kt`, `*.kts`; `:notti` `lint { warningsAsErrors = true; abortOnError = true }`.
**Where**: root `build.gradle.kts`, `notti/build.gradle.kts`
**Depends on**: T5
**Requirement**: PKG-10

**Done when**:

- [x] `spotlessCheck` and `:notti:lintRelease` pass
- [x] A 2-space indented line fails `spotlessCheck`; a lint warning (`SimpleDateFormat` without a locale) fails `lintRelease` (both reverted)

**Tests**: none
**Gate**: Build + `./gradlew spotlessCheck :notti:lintRelease`
**Commit**: `build: add ktlint and lint warnings as errors`

---

### T7: ABI guard

**What**: BCV 0.18.2 wired by hand as in AD-012 (`apiBuild`/`apiDump`/`apiCheck`), dump committed to `notti/api/notti.api`, check hooked into `check`.
**Where**: `notti/build.gradle.kts`, `notti/api/`
**Depends on**: T6
**Requirement**: PKG-13

**Done when**:

- [x] Dump committed, contains no symbol (everything is `internal`)
- [x] Making `NottiSdkVersion` `public` fails the check (reverted)

**Tests**: none
**Gate**: Build + ABI check command
**Commit**: `build: guard the public API with an ABI dump`

---

### T8: Publication to a local Maven repository

**What**: vanniktech plugin: coordinates `com.zeeplabs:notti-android:<constant>`, single release variant with sources and javadoc jars, POM (name, description, url, MIT, developer, SCM `github.com/zeeplabs/zeep-notti-android-sdk`, remote `git@github.com:zeeplabs/zeep-notti-android-sdk.git`, confirmed by Julio 2026-10-28), repository `build` at `build/repo`, signing only when a key property exists.
**Where**: `notti/build.gradle.kts`, `gradle.properties`
**Depends on**: T7
**Requirement**: PKG-02

**Done when**:

- [x] `:notti:publishAllPublicationsToBuildRepository` writes `.aar`, `.pom`, `-sources.jar`, `-javadoc.jar`, `.module`
- [x] POM has the coordinates, license, SCM, `firebase-messaging` as `compile` scope, no `com.facebook.react`
- [x] Runs with no signing key and no network credentials

**Tests**: none
**Gate**: Build + publish command + POM inspection
**Commit**: `build: publish the AAR to a local Maven repository`

---

### T9: `:example` placeholder

**What**: Add `:example` to `settings.gradle.kts` (T4 included only `:notti`: an included project without a directory fails a clean checkout). App module `com.zeeplabs.notti.example`, one empty Activity, `notti.usePublished` switch (project dependency by default, published coordinates when `true`), Google Services plugin applied only if `google-services.json` exists.
**Where**: `example/`
**Depends on**: T8
**Requirement**: PKG-03

**Done when**:

- [ ] `:example:assembleDebug` passes in both modes (publish first in a separate invocation for `true`)
- [ ] `:example:dependencies --configuration debugRuntimeClasspath` shows `com.zeeplabs:notti-android:0.1.0` in published mode

**Tests**: none
**Gate**: Build (both modes)
**Commit**: `feat(example): add placeholder app consuming the published AAR`

---

### T10: `scripts/check-version.sh` + self-test [P]

**What**: Script per design (constant, top released CHANGELOG section, `GITHUB_REF` tag `X.Y.Z`); `scripts/tests/check-version-test.sh` with fixtures (ok, missing file, no match, CHANGELOG drift, tag drift, undated tag build); `scripts/tests/run.sh` runner.
**Where**: `scripts/`
**Depends on**: T2
**Requirement**: PKG-07

**Done when**:

- [ ] Every fixture gives the expected exit code; runs on macOS (BSD sed) and in the Ubuntu CI job

**Tests**: script self-test
**Gate**: Scripts
**Commit**: `ci: add version drift check`

---

### T11: `scripts/check-package-name.sh` + self-test [P]

**What**: Script per design (no `package`/`import com.notti`, namespace check, grep exit 2 fails); fixture test (clean tree, `package com.notti`, `import com.notti.NottiCore`, `com.zeeplabs.notti` allowed, unreadable dir).
**Where**: `scripts/`
**Depends on**: T2
**Requirement**: PKG-04

**Done when**:

- [ ] Every fixture gives the expected exit code

**Tests**: script self-test
**Gate**: Scripts
**Commit**: `ci: forbid the com.notti package`

---

### T12: CI workflow

**What**: `.github/workflows/ci.yml` with jobs `checks` and `build` per design (SHA-pinned actions, wrapper validation, Gradle cache, AAR/POM content checks, published-mode example, test reports on failure, no `continue-on-error`).
**Where**: `.github/workflows/ci.yml`
**Depends on**: T9, T10, T11
**Requirement**: PKG-09, PKG-10, PKG-12

**Done when**:

- [ ] `actionlint` clean; Full gate passes locally
- [ ] After Julio OKs creating the remote and opening a PR: one green run, and red runs proven for a failing test, a lint error, an ABI change without dump, a version drift and a `com.notti` package (throwaway commits, then dropped)

**Tests**: none
**Gate**: Full + CI run
**Commit**: `ci: add build, test, lint and packaging workflow`

---

### T13: AGENTS.md, CLAUDE.md, TESTING.md

**What**: `AGENTS.md` (source of truth `.specs/`, port rule AD-002, package AD-003, logging AD-011, ABI AD-010 with the update command, toolchain AD-012, commands, guards, git rules), `CLAUDE.md` pointing to it, `.specs/codebase/TESTING.md` (commands, infra, coverage matrix and gates from this file, ABI task names from T1).
**Where**: repo root, `.specs/codebase/`
**Depends on**: T12
**Requirement**: PKG-14

**Done when**:

- [ ] Every command in AGENTS.md and TESTING.md runs as written
- [ ] ROADMAP `package-foundation` marked done after validation

**Tests**: none
**Gate**: run each documented command
**Commit**: `docs: add agent rules and testing guide`

---

## Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1 | one spike report | ✅ |
| T2 | repo hygiene files (one concern, no code) | ✅ |
| T3 | Gradle root config | ✅ |
| T4 | library module + its one constant and test | ✅ (one module; constant is what the module build reads) |
| T5 | two smoke tests of one concern | ✅ |
| T6 | lint gates | ✅ |
| T7 | ABI guard | ✅ |
| T8 | publication config | ✅ |
| T9 | example module | ✅ |
| T10 | one script + its test | ✅ |
| T11 | one script + its test | ✅ |
| T12 | one workflow file | ✅ |
| T13 | contributor docs | ✅ |

## Diagram-Definition Cross-Check

| Task | Depends on (definition) | Diagram | Match |
| --- | --- | --- | --- |
| T1 | None | start | ✅ |
| T2 | T1 | T1 → T2 | ✅ |
| T3 | T2 | T2 → T3 | ✅ |
| T4 | T3 | T3 → T4 | ✅ |
| T5 | T4 | T4 → T5 | ✅ |
| T6 | T5 | T5 → T6 | ✅ |
| T7 | T6 | T6 → T7 | ✅ |
| T8 | T7 | T7 → T8 | ✅ |
| T9 | T8 | T8 → T9 | ✅ |
| T10 | T2 | T2 → T10 [P] | ✅ |
| T11 | T2 | T2 → T11 [P] | ✅ |
| T12 | T9, T10, T11 | T9, T10, T11 → T12 | ✅ |
| T13 | T12 | T12 → T13 | ✅ |

## Test Co-location Validation

| Task | Layer | Matrix requires | Tests field | OK |
| --- | --- | --- | --- | --- |
| T4 | `NottiSdkVersion` | unit | unit | ✅ |
| T5 | test infrastructure | unit | unit | ✅ |
| T3, T6, T7, T8, T9 | Gradle build | none + negative proof | none (proof in Done when) | ✅ |
| T10, T11 | scripts | script self-test | script self-test | ✅ |
| T12 | CI | none | none | ✅ |
| T1, T2, T13 | spike/docs | none | none | ✅ |

## Requirement Coverage

| Requirement | Tasks |
| --- | --- |
| PKG-01 | T1, T3, T4 |
| PKG-02 | T1, T8 |
| PKG-03 | T9 |
| PKG-04 | T11 |
| PKG-05 | T1, T4 |
| PKG-06 | T4 |
| PKG-07 | T10 |
| PKG-08 | T4 |
| PKG-09 | T12 |
| PKG-10 | T6, T12 |
| PKG-11 | T5 |
| PKG-12 | T3, T12 |
| PKG-13 | T1, T7 |
| PKG-14 | T2, T13 |

14/14 mapped.
