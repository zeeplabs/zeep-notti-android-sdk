# Example App and Release 0.1.0 Tasks

**Design**: `.specs/features/example-and-release/design.md`
**Testing**: `.specs/codebase/TESTING.md`
**Status**: In progress (T1-T10 done)

---

## Execution Protocol (MANDATORY -- do not skip)

`tlc-spec-driven` Execute flow; rules in `AGENTS.md`. One task per commit. No SDK code changes: nothing under `notti/src/` changes; `notti/build.gradle.kts` changes only in T7. The unit suite stays at 718. Example code follows the privacy rule of EXR-03 (no value of a client key, token, email, phone, user id, data or URL on screen or in a log). No real app id, key or `google-services.json` is ever created in the repo by the agent. Push, PR, tag, Maven Central upload and Portal publish only by Julio or with his explicit OK (AD-015).

---

## Test Coverage Matrix

| Layer | Required verification | Location | Run command |
| --- | --- | --- | --- |
| Guard scripts | script self-test | `scripts/tests/*-test.sh` | `scripts/tests/run.sh` |
| Example config and Firebase plugin | Gradle builds with and without local files | `example/`, `example-manual-fcm/` | see T1, T2 |
| Signed publication | end-to-end script | `scripts/check-release-artifacts.sh` | itself (needs `gpg`) |
| Workflows | static validation | `.github/workflows/` | `actionlint` if installed, else YAML parse |
| Docs | review + guards that read them (`check-version.sh`) | `README.md`, `docs/`, `CHANGELOG.md` | `scripts/check-version.sh` |
| Device behavior | manual checklist | `docs/DEVICE_CHECKLIST.md` | Julio |

## Gate Check Commands

| Gate level | When | Command |
| --- | --- | --- |
| Example | T1-T4 | `./gradlew spotlessCheck :example:assembleDebug :example:assembleRelease :example-manual-fcm:assembleDebug && scripts/tests/run.sh` |
| Full | T7, T11 | TESTING.md "Full" + `scripts/check-no-url-open.sh` + `scripts/check-example-coverage.sh` + `scripts/check-no-tracked-config.sh` + `scripts/check-release-artifacts.sh` (when `gpg` is available; otherwise reported as not run) |

---

## Execution Plan

Gradle work is not parallel-safe, so tasks run in order. Docs (T5, T6, T9) could run in parallel with code but follow the order to keep one task per commit with a clean tree.

```
T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 → T11
```

---

## Task Breakdown

### T1: Example config and conditional Google Services plugin

**What**: D1. Catalog `googleServices = "4.5.0"` + plugin alias; root `apply false`; both example modules apply it only when their `google-services.json` exists; `notti.properties` read into `BuildConfig` (`NOTTI_APP_ID`, `NOTTI_CLIENT_KEY`, `NOTTI_BASE_URL`); `example/notti.properties.sample`; `.gitignore` `notti.properties`; `ExampleConfig.isComplete`; `ExampleApplication` initializes only when complete; `scripts/tests/fixtures/google-services.fixture.json`.
**Where**: `gradle/libs.versions.toml`, `build.gradle.kts`, `example/build.gradle.kts`, `example-manual-fcm/build.gradle.kts`, `example/notti.properties.sample`, `.gitignore`, `example/src/main/java/.../ExampleConfig.kt`, `ExampleApplication.kt`, manual-fcm `Application` (new, init only), fixture json
**Depends on**: None
**Requirement**: EXR-01, EXR-04, EXR-05, EXR-06 (files)

**Done when**:

- [x] Example gate green with no local files
- [x] With the fixture copied to `example/google-services.json` (removed after): `:example:assembleDebug` passes and merged resources have the fixture `google_app_id`; same for `:example-manual-fcm`
- [x] With a temp `example/notti.properties` (synthetic values, removed after): `BuildConfig` has them; without it, the fields are empty
- [x] `-Pnotti.usePublished=true` builds still pass

**Tests**: Gradle builds **Gate**: Example
**Commit**: `build(example): local Notti config and conditional Google Services plugin`
**Status**: Done. The fixture has one client per example package (`...0000` and `...0001`); `:example-manual-fcm` gets its own `ManualFcmApplication` with an inline config check (it cannot see `ExampleConfig`).

---

### T2: Tracked-config guard and the CI Firebase build

**What**: D4 script + self-test; D5 CI steps: guard in `checks`, fixture build and `google_app_id` assertion in `build`.
**Where**: `scripts/check-no-tracked-config.sh`, `scripts/tests/check-no-tracked-config-test.sh`, `.github/workflows/ci.yml`
**Depends on**: T1
**Requirement**: EXR-05, EXR-06

**Done when**:

- [x] Self-test covers clean, tracked `google-services.json`, tracked `notti.properties`, tracked `.sample` / `.fixture.json` (pass), not a git checkout (exit 2)
- [x] Guard passes on this repo; the CI steps run locally as written (the assertion command)

**Tests**: script self-test **Gate**: Example
**Commit**: `ci: guard against tracked app config and build the example with Firebase config`
**Status**: Done. The CI step builds and asserts both example apps (each against its own fixture client), not only `:example`; self-test also covers a root `google-services.json` and an untracked one.

---

### T3: Example screen with every public call

**What**: D2. `ExampleEventLog`; sections Status, Permission, Identity, User, Device, Notifications, In-App, Event log; listeners in `ExampleApplication` before `initialize`; fully qualified calls; redacted lines only.
**Where**: `example/src/main/java/com/zeeplabs/notti/example/` (`MainActivity.kt`, `ExampleApplication.kt`, `ExampleEventLog.kt`, small view helpers if needed)
**Depends on**: T1
**Requirement**: EXR-02 (calls), EXR-03, EXR-04 (screen)

**Done when**:

- [x] Every member listed in D3 for `Notti`, `User`, `Notifications`, `InAppMessages` is called or shown
- [x] `grep` of the example sources shows no `Log.` call with a typed value, token, data value or URL
- [x] "Not configured" state visible without `notti.properties`
- [x] Example gate green (debug, minified release, published AAR)

**Tests**: Gradle builds; device checks in T6 **Gate**: Example
**Commit**: `feat(example): one screen with every public call and a redacted event log`
**Status**: Done. Deviations: Status has no SDK version (`NottiSdkVersion` is internal, not in `notti.api`); subscription and location use check boxes that start unchecked (the SDK has no getters); `ExampleEventLog` posts to the main thread when a listener calls it off-main.

---

### T4: Example coverage guard

**What**: D3 script + self-test + CI step in `checks`.
**Where**: `scripts/check-example-coverage.sh`, `scripts/tests/check-example-coverage-test.sh`, `scripts/tests/fixtures/` (api and source fixtures), `.github/workflows/ci.yml`
**Depends on**: T3
**Requirement**: EXR-02, EXR-20

**Done when**:

- [x] Self-test cases of D3 pass; exit 1 vs exit 2 distinguished
- [x] Passes on the repo; removing one call from the example makes it fail naming the member (checked, then reverted)

**Tests**: script self-test **Gate**: Example
**Commit**: `ci: fail when the example does not call a public method`
**Status**: Done. Fixtures are built inline in the self-test's temp dirs (as the other self-tests do), not under `scripts/tests/fixtures/`. A getter/setter name also covers its property (Java sources). Removing `Notti.User.clearPhone()` failed naming it; reverted.

---

### T5: README for integrators

**What**: D9 README: EXR-07 order, data collected and storage keys, Data safety answers (marked "to confirm by the app owner"), LGPD note, known limitations, events and logout wording (SDKOPEN-11, DLOG-22), example app section. Removes every "draft" marker. Banner untouched (T10).
**Where**: `README.md`
**Depends on**: T3 (example section names real files)
**Requirement**: EXR-07, EXR-08, EXR-09, EXR-10, EXR-11

**Done when**:

- [x] Sections in EXR-07 order; no "draft"
- [x] Every field in the data section traced to the code that sends or stores it (list in the commit body)
- [x] Only placeholders; default host stated once
- [x] Every Kotlin snippet matches the current public API (checked against `notti.api`)

**Tests**: review **Gate**: `scripts/check-version.sh`
**Commit**: `docs: integrator README for 0.1.0`
**Status**: Done. Data section traced in the commit body. `logout` wording follows the code: email and phone are cleared on the device and the clears are also sent (`NottiCore.logout` calls `clearEmail`/`clearPhone`). Requirements state `compileSdk` 36 from the AAR's `minCompileSdk`. Device checklist link points at `docs/DEVICE_CHECKLIST.md` (T6).

---

### T6: Device checklist

**What**: D9 `docs/DEVICE_CHECKLIST.md`: every EXR-12 item as `DC-NN` with source, device/API, prerequisites, steps, expected, result box; emulator-capable items marked; header for run metadata.
**Where**: `docs/DEVICE_CHECKLIST.md`
**Depends on**: T3 (steps name example controls)
**Requirement**: EXR-12, EXR-13

**Done when**:

- [x] Every EXR-12 item present; every `STATE.md` todo and traceability gap it closes cited
- [x] Steps use only example controls, `adb` and the Notti dashboard

**Tests**: review **Gate**: none
**Commit**: `docs: device checklist for 0.1.0`
**Status**: Done. 25 items (DC-01..DC-25), plus DC-02 (not-configured state). Some items need a temporary, uncommitted change (provider removal, `singleTop`, `opensInAppUrls = false`, RN upgrade `applicationId`). DC-05 (location) has a Not run box: the example declares no location permission.

---

### T7: Maven Central upload wiring and the signed dry run

**What**: D6 (`publishToMavenCentral()`, unsigned-upload guard) and D7 (`check-release-artifacts.sh`), CI step in `build` after the publish step.
**Where**: `notti/build.gradle.kts`, `scripts/check-release-artifacts.sh`, `.github/workflows/ci.yml`, `.specs/codebase/TESTING.md` (Full gate + `gpg` note)
**Depends on**: T2 (same CI file, ordered to avoid conflicts)
**Requirement**: EXR-16 (wiring), EXR-17, EXR-20

**Done when**:

- [x] `publishAllPublicationsToBuildRepository` still runs with no credentials and no key
- [x] `./gradlew :notti:publishToMavenCentral` without a key fails at the guard, before any network call (run with no credentials set)
- [x] `check-release-artifacts.sh` passes (run locally by Julio after `brew install gnupg`: 5 signatures and 6 POM fields ok; before that, exit 2 with the hint) where `gpg` exists (CI); locally, if `gpg` is absent, its exit 2 and hint are shown and reported as not run
- [x] Full gate green

**Tests**: end-to-end script **Gate**: Full
**Commit**: `build: wire the Maven Central upload and check signed artifacts`
**Status**: Done. Deviation from D6: the guard is a `doFirst` on the plugin's `prepareMavenCentralPublishing`, not on `publishToMavenCentral`: the latter is a lifecycle task whose actions run after the upload. Proven with `--offline`, with and without fake Portal credentials: fails at the guard first. The POM field parser was checked against the real POM and an emptied field; the signed run passed locally (in-memory key without passphrase works with vanniktech 0.37.0).

---

### T8: Release workflow

**What**: D8: `release.yml` (tag trigger, `ci` via `workflow_call`, `upload` job in environment `maven-central`, secret presence step, `publishToMavenCentral`); `ci.yml` gets `workflow_call` and loses the `tags` push trigger. Actions pinned by SHA with version comments (reuse the pins already in `ci.yml`).
**Where**: `.github/workflows/release.yml`, `.github/workflows/ci.yml`
**Depends on**: T7
**Requirement**: EXR-16

**Done when**:

- [x] `actionlint` clean if available, else both files parse as YAML and the job graph is reviewed
- [x] No secret echoed; env vars passed only to the upload step
- [x] The tag pattern rejects `v0.1.0` and accepts `0.1.0`

**Tests**: static **Gate**: none (cannot run before B-001/B-002)
**Commit**: `ci: release workflow uploads a signed 0.1.0 to the Central Portal on tag`
**Status**: Done (static only: `actionlint` not installed, both files parse; tag pattern checked as a regex: `0.1.0` yes, `v0.1.0`/`0.1.0-rc1` no). Deviation from D8: the release concurrency group is `release-upload-<ref>`, because GitHub concurrency groups are case-insensitive and the called CI's group `Release-<ref>` would collide with `release-<ref>`.

---

### T9: Release runbook

**What**: D9 `docs/RELEASING.md`: one-time setup, release steps (CI path and local fallback with env vars), Portal review and Publish, GitHub Release, post-release, failure handling.
**Where**: `docs/RELEASING.md`
**Depends on**: T8
**Requirement**: EXR-18

**Done when**:

- [x] Every command matches T7/T8 names (task, properties, secrets, environment)
- [x] No secret value, key id or token in the file; placeholders only

**Tests**: review **Gate**: none
**Commit**: `docs: release runbook`
**Status**: Done. Names match T7/T8 (`publishToMavenCentral`, `mavenCentralUsername`/`Password`, `signingInMemoryKey`/`KeyPassword`, the four secrets, environment `maven-central`). The local fallback keeps the key out of `gradle.properties` and exports it into the shell for the upload only. [Incerto] Portal menu labels and the `zeeplabs.com` domain are to confirm at setup (B-001).

---

### T10: CHANGELOG 0.1.0 and README banner

**What**: `## [0.1.0] - TBD` (Added, Fixed, Known limitations, Migration from `react-native-notti` 0.8.0), empty `[Unreleased]` above it; README banner describes 0.1.0 and points to the checklist until published.
**Where**: `CHANGELOG.md`, `README.md`
**Depends on**: T5, T9
**Requirement**: EXR-14, EXR-15

**Done when**:

- [x] `scripts/check-version.sh` passes; with `GITHUB_REF=refs/tags/0.1.0` it fails only on the `TBD` date (expected until release day)
- [x] Banner no longer says pre-release

**Tests**: `check-version.sh` **Gate**: `scripts/tests/run.sh`
**Commit**: `docs: CHANGELOG and README for 0.1.0`
**Status**: Done. Tag check with `refs/tags/0.1.0` fails only on `TBD`; on a copy with a date it passes; `v0.1.0` fails. The earlier `[Unreleased]` entries moved into 0.1.0 Added/Fixed. `RELEASING.md` step 4 now says to drop the banner's "Until the release is published" sentence.

---

### T11: Traceability, state and the release PR description

**What**: `traceability.md` (EXR → file/check), spec statuses, `STATE.md` (current work, todos closed by this feature vs. waiting on the checklist), `ROADMAP.md`, TESTING.md if T7 did not cover it; release PR description in the scratchpad with the EXR-19 gates.
**Where**: `.specs/features/example-and-release/traceability.md`, `spec.md`, `.specs/project/STATE.md`, `ROADMAP.md`; scratchpad `pr-example-release.md`
**Depends on**: T10
**Requirement**: EXR-19, all (traceability)

**Done when**:

- [ ] 20/20 EXR mapped
- [ ] Full gate green; counts reported (718 unit tests, self-tests)

**Tests**: Full gate **Gate**: Full
**Commit**: `docs(specs): example-and-release traceability`

---

## Requirement → Task

| Requirement | Task |
| --- | --- |
| EXR-01 | T1, T3 |
| EXR-02 | T3, T4 |
| EXR-03 | T3 |
| EXR-04 | T1, T3 |
| EXR-05 | T1, T2 |
| EXR-06 | T1, T2 |
| EXR-07..EXR-11 | T5 |
| EXR-12, EXR-13 | T6 |
| EXR-14, EXR-15 | T10 |
| EXR-16 | T7, T8 |
| EXR-17 | T7 |
| EXR-18 | T9 |
| EXR-19 | T11 |
| EXR-20 | T4, T7 (T2 adds the tracked-config step) |

**Coverage:** 20/20 mapped.
