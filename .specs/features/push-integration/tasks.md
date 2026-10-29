# Push Integration Tasks

**Design**: `.specs/features/push-integration/design.md`
**Testing**: `.specs/codebase/TESTING.md`
**Status**: Draft (2026-10-29)

---

## Execution Protocol (MANDATORY -- do not skip)

`tlc-spec-driven` Execute flow; rules in `AGENTS.md`. Port tasks use `scripts/port-file.sh` and `scripts/port-diff.sh` with the allowed changes of `core-port` plus this design's list. One task per commit, tests in the same commit. Push/PR only with Julio's OK.

---

## Test Coverage Matrix

| Code Layer | Required Test Type | Location Pattern | Run Command |
| --- | --- | --- | --- |
| Ported classes | unit 1:1 (Robolectric where RN uses it) | `notti/src/test/java/com/zeeplabs/notti/<Class>Test.kt` | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` |
| New classes (requester, Activity, tracker, provider wiring) | unit (Robolectric) | same | same |
| Library manifest | unit (Robolectric `PackageManager`) | `NottiManifestTest.kt` | same |
| Scripts | script self-test | `scripts/tests/*-test.sh` | `scripts/tests/run.sh` |
| Build proofs (manual FCM module, minified example) | none (build) + merged-manifest check | - | Build gate |

## Gate Check Commands

| Gate Level | When | Command |
| --- | --- | --- |
| Quick | each code task | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` + `spotlessCheck` |
| Full | after T8 and T10 | TESTING.md "Full" + `scripts/check-no-url-open.sh` + `./gradlew :example-manual-fcm:assembleDebug :example:assembleRelease` |

---

## Execution Plan

Gradle tasks sequential; T9 scripts only.

```
T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T10
T9 [P]
T9 → T10
```

---

## Task Breakdown

### T1: Port the FCM service and payload parsing

**What**: Port `NottiFirebaseMessagingService.kt` (`ParsedNotification`, `parseRemoteMessage`, service) and its 6 tests; add `NottiRuntime.onNotificationReceived`.
**Where**: `NottiFirebaseMessagingService.kt`, `NottiRuntime.kt`, test
**Depends on**: None
**Requirement**: PUSH-02, PUSH-03, PUSH-04

**Done when**:

- [x] 6/6 pass; received event enqueued through the runtime with no core; token reaches the live core, dropped with none
- [x] `port-diff` only allowed changes

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(push): port the FCM service and payload parsing`

---

### T2: Port the click relay

**What**: Port `NottiNotificationClickRelay.kt` unchanged.
**Where**: `NottiNotificationClickRelay.kt`, small relay test (emit/buffer/take-once/identity detach) since RN covered it only through the module
**Depends on**: T1
**Requirement**: PUSH-10

**Done when**:

- [x] Relay test green; `port-diff` shows only `internal`

**Tests**: unit
**Gate**: Quick
**Commit**: `feat(push): port the notification click relay`

---

### T3: Port tap detection and track the current Activity

**What**: Port `NottiActivityLifecycleListener.kt` (`parseClickIntentExtras`, listener) and its 20 tests; add the current-Activity weak reference (set on resume, cleared on destroy) read by `NottiRuntime.currentActivity` + `NottiCurrentActivityTest`.
**Where**: `NottiActivityLifecycleListener.kt`, `NottiRuntime.kt`, tests
**Depends on**: T2
**Requirement**: PUSH-09, PUSH-10, PUSH-11, PUSH-19

**Done when**:

- [x] 20/20 ported pass; tracker tests pass
- [x] `port-diff`: only allowed changes plus the tracker lines (listed in the commit body)

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(push): port tap detection and track the current Activity`

---

### T4: Permission Activity and requester

**What**: `NottiPermissionActivity` + `NottiPermissionRequester` per design, its manifest entry, `NottiPermissionRequesterTest`, and the ported RN module case "requestPermission on Android below 13 …".
**Where**: `NottiPermissionActivity.kt`, `NottiPermissionRequester.kt`, `AndroidManifest.xml`, tests
**Depends on**: T3
**Requirement**: PUSH-16, PUSH-17, PUSH-18

**Done when**:

- [ ] API 32 true without an Activity; API 34 no Activity false; granted/denied resolve once; two concurrent requests resolve once each; flag set before the dialog; `startActivity` failure resolves false

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(push): request the notification permission from any Activity`

---

### T5: Wire the requester and the Activity into the runtime

**What**: `NottiRuntime.start` default `permissionRequester` = the requester; providers read the tracked Activity; test that `permission_status` is sent on API 34 with a resumed Activity (closes the core-port accepted exception; update `core-port/traceability.md`).
**Where**: `NottiRuntime.kt`, `NottiRuntimeTest.kt`, `.specs/features/core-port/traceability.md`
**Depends on**: T4
**Requirement**: PUSH-19

**Done when**:

- [ ] `permission_status` present in the PATCH on API 34 (rationale read from the tracked Activity); core-port exception marked closed

**Tests**: unit (Robolectric + MockWebServer)
**Gate**: Quick
**Commit**: `feat(push): wire the permission requester and current Activity into the runtime`

---

### T6: Port the process-start hook

**What**: Port `NottiInitProvider` (lines 1-100 of the RN file) onto the runtime; manifest provider with the new authority; `NottiInitProviderTest`.
**Where**: `NottiInitProvider.kt`, `AndroidManifest.xml`, test
**Depends on**: T5
**Requirement**: PUSH-05, PUSH-06, PUSH-07

**Done when**:

- [ ] Runtime created, listener registered once, network observer registered, foreground observer added after the main-looper post; no request reaches MockWebServer; a throwing step does not stop the others

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(push): port the process-start hook`

---

### T7: NottiBridge (first public API)

**What**: Port `NottiBridge.kt` as `public`, `@JvmStatic`, sharing the service's internal paths; port its 2 tests; `apiDump` and commit the dump.
**Where**: `NottiBridge.kt`, `notti/api/notti.api`, test
**Depends on**: T6
**Requirement**: PUSH-13, PUSH-14

**Done when**:

- [ ] 2/2 pass; safe no-ops with no runtime/core; `notti.api` lists exactly `NottiBridge` and its two methods

**Tests**: unit
**Gate**: Quick + `:notti:apiCheck`
**Commit**: `feat(push): add NottiBridge for hosts with their own FCM service`

---

### T8: Manifest test and build proofs

**What**: `NottiManifestTest` (service, provider, activity, permission via `PackageManager`); `:example-manual-fcm` module (removes our service, forwards through `NottiBridge`); CI steps: assemble it and grep its merged manifest; minified `:example` release build proving R8 keeps the components (consumer rules stay comment-only if so).
**Where**: `notti/src/test/…/NottiManifestTest.kt`, `example-manual-fcm/`, `settings.gradle.kts`, `.github/workflows/ci.yml`, `example/build.gradle.kts`
**Depends on**: T7
**Requirement**: PUSH-01, PUSH-07, PUSH-15

**Done when**:

- [ ] Manifest test green; manual module merged manifest has its service and not `com.zeeplabs.notti.NottiFirebaseMessagingService`; minified example builds and the release APK's dex contains the three component classes
- [ ] `actionlint` clean

**Tests**: unit (Robolectric) + build
**Gate**: Full
**Commit**: `test(push): prove the merged manifest and the manual FCM path`

---

### T9: URL-open guard [P]

**What**: `scripts/check-no-url-open.sh` per design + self-test + CI step in `checks`.
**Where**: `scripts/`, `.github/workflows/ci.yml`
**Depends on**: None
**Requirement**: PUSH-12

**Done when**:

- [x] Self-test: clean tree passes; `ACTION_VIEW`, `CustomTabsIntent`, `openUri` each fail; an allowlisted exact path passes; grep status 2 fails (macOS + Ubuntu 24.04, shellcheck and actionlint clean)
- [x] Runs green on the current tree

**Tests**: script self-test
**Gate**: Scripts
**Commit**: `ci: forbid opening URLs outside the allowlist`

---

### T10: Traceability and docs

**What**: `.specs/features/push-integration/traceability.md` (PUSH → tests; RN IDs owned here from core-port's list; counts vs RN; port-diff summary; suppressions); README section on the manual FCM path, provider removal and `setIntent` (draft, final in `example-and-release`); spec statuses; ROADMAP/STATE.
**Where**: `.specs/features/push-integration/`, `README.md`
**Depends on**: T8, T9
**Requirement**: all PUSH (verification)

**Done when**:

- [ ] Every PUSH-xx and every RN ID routed here mapped to a test; full gate green

**Tests**: none
**Gate**: Full
**Commit**: `docs(specs): push-integration traceability`

---

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Match |
| --- | --- | --- | --- |
| T1 | None | start | ✅ |
| T2 | T1 | T1 → T2 | ✅ |
| T3 | T2 | T2 → T3 | ✅ |
| T4 | T3 | T3 → T4 | ✅ |
| T5 | T4 | T4 → T5 | ✅ |
| T6 | T5 | T5 → T6 | ✅ |
| T7 | T6 | T6 → T7 | ✅ |
| T8 | T7 | T7 → T8 | ✅ |
| T9 | None | T9 [P] | ✅ |
| T10 | T8, T9 | T8 → T10, T9 → T10 | ✅ |

## Test Co-location Validation

| Task | Layer | Matrix | Tests | OK |
| --- | --- | --- | --- | --- |
| T1, T3 | ported | unit 1:1 | unit | ✅ |
| T2, T4, T5, T6, T7 | ported/new | unit | unit | ✅ |
| T8 | manifest + build | unit + build | unit + build | ✅ |
| T9 | scripts | self-test | self-test | ✅ |
| T10 | docs | none | none | ✅ |

## Requirement Coverage

| Requirement | Tasks |
| --- | --- |
| PUSH-01 | T8 |
| PUSH-02 | T1 |
| PUSH-03 | T1 |
| PUSH-04 | T1 |
| PUSH-05 | T6 |
| PUSH-06 | T6 |
| PUSH-07 | T6, T8 |
| PUSH-08 | T10 |
| PUSH-09 | T3 |
| PUSH-10 | T2, T3 |
| PUSH-11 | T3 |
| PUSH-12 | T9 |
| PUSH-13 | T7 |
| PUSH-14 | T7 |
| PUSH-15 | T8 |
| PUSH-16 | T4 |
| PUSH-17 | T4 |
| PUSH-18 | T4 |
| PUSH-19 | T3, T5 |

19/19 mapped.
