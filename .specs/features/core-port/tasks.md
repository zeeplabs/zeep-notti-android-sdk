# Core Port Tasks

**Design**: `.specs/features/core-port/design.md`
**Testing**: `.specs/codebase/TESTING.md`
**Status**: Draft (2026-10-28)

---

## Execution Protocol (MANDATORY -- do not skip)

Implement with the `tlc-spec-driven` skill (Execute flow). Rules in `AGENTS.md`. Port tasks: copy the RN file from tag `v0.8.0` (`git -C ../zeep-notti-react-native show v0.8.0:android/src/main/java/com/notti/<File>.kt`), apply only the design's "allowed changes", then check with `scripts/port-diff.sh` (T13). One task per commit; tests in the same commit. Push and PR only with Julio's OK.

---

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Ported classes | unit (ported 1:1) | RN count per class or more | `notti/src/test/java/com/zeeplabs/notti/<Class>Test.kt` | `./gradlew :notti:testDebugUnitTest --tests 'com.zeeplabs.notti.<Class>Test'` |
| New classes (runtime, providers, logger) | unit (Robolectric where Context is needed) | every interface method, failure paths | same | same |
| Seams (interfaces only) | none | compile | - | Build gate |
| Scripts | script self-test | pass + each failure | `scripts/tests/*-test.sh` | `scripts/tests/run.sh` |
| Traceability | none | review | - | - |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | after each code task | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` + `./gradlew spotlessCheck` |
| Full | after T12 and T14 | TESTING.md "Full" + `scripts/check-no-react-native.sh` |

---

## Execution Plan

All Gradle tasks sequential (TESTING.md: not parallel-safe). T13 is scripts only.

```
T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 → T11 → T12 → T14
T13 [P]
T13 → T14
```

---

## Task Breakdown

### T1: NottiLogger

**What**: `NottiLogger` per design (levels, default `ERROR`, swappable sink, lazy overloads) + `NottiLoggerTest`.
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiLogger.kt`
**Depends on**: None
**Requirement**: CORE-15, CORE-16

**Done when**:

- [x] Default level emits `error` only; `NONE` emits nothing; lazy message not evaluated below level
- [x] Default sink tag `Notti` (Robolectric `ShadowLog`)

**Tests**: unit
**Gate**: Quick
**Commit**: `feat(core): add NottiLogger`

---

### T2: Port NottiDeviceStore

**What**: Port `NottiDeviceStore.kt` and its 20 tests; record every key with its `put*`/`get*` type in `traceability.md` (section "Storage contract").
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiDeviceStore.kt`, test
**Depends on**: T1
**Reuses**: RN `NottiDeviceStore.kt`, `NottiDeviceStoreTest.kt`
**Requirement**: CORE-01, CORE-09, CORE-12

**Done when**:

- [x] 20/20 tests pass; `port-diff` shows only allowed changes (run after T13 if not yet available, noted in T14)
- [x] Key/type table written

**Tests**: unit
**Gate**: Quick
**Commit**: `feat(core): port NottiDeviceStore`

---

### T3: Port NottiEventStore

**What**: Port `NottiEventStore.kt` (+ `NottiEventType`, `PendingEvent`) and its 7 tests.
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiEventStore.kt`, test
**Depends on**: T2
**Requirement**: CORE-01, CORE-09, CORE-12

**Done when**:

- [x] 7/7 pass, incl. the corrupt-JSON self-heal case

**Tests**: unit
**Gate**: Quick
**Commit**: `feat(core): port NottiEventStore`

---

### T4: Port the In-App data files

**What**: Port `NottiInAppModels.kt` and `NottiInAppEventStore.kt` with `NottiInAppModelsTest` (36) and `NottiInAppEventStoreTest` (13). Data and persistence only (design amendment 2026-10-28).
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiInAppModels.kt`, `NottiInAppEventStore.kt`, tests
**Depends on**: T3
**Requirement**: CORE-01, CORE-09, CORE-12

**Done when**:

- [x] 36/36 and 13/13 pass; `port-diff` shows only allowed changes

**Tests**: unit
**Gate**: Quick
**Commit**: `feat(core): port the In-App data models and event store`

---

### T5: In-App seams

**What**: `NottiInAppHooks` extracted verbatim from RN `NottiInAppCoordinator.kt:45`; new `NottiInAppLifecycle` (4 methods).
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiInAppHooks.kt`, `NottiInAppLifecycle.kt`
**Depends on**: T4
**Requirement**: CORE-01, CORE-02

**Done when**:

- [x] Both compile; `NottiInAppHooks` identical to RN under `diff -w` (package aside)

**Tests**: none
**Gate**: Build
**Commit**: `feat(core): add In-App seams for the core and observers`

---

### T6: Port NottiApiClient and NottiCore

**What**: Port `NottiApiClient.kt` (48 tests, MockWebServer) and `NottiCore.kt` (incl. `SessionGate`, companion helpers, `formatIsoUtc`; 128 tests) together: each needs the other to compile (design amendment 2026-10-28). Also port `NottiForegroundObserver` (from RN `NottiInitProvider.kt`, own file) because `NottiCoreTest` calls it; temporary null providers per the design's second amendment; its own RN tests stay in T9. Apply allowed change 5 as amended (no exception message in any log line or failure message).
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiApiClient.kt`, `NottiCore.kt`, `NottiForegroundObserver.kt`, tests
**Depends on**: T5
**Requirement**: CORE-01, CORE-02, CORE-09, CORE-12, CORE-15

**Done when**:

- [x] 48/48 and 128/128 pass (retry 5 attempts, 2/4/8/16 s via the injected sleeper, transient 408/429/5xx/network)
- [x] Only allowed changes (KDoc `NottiModule` mentions rewritten; `t.message`/`e.message` replaced as listed)

**Tests**: unit
**Gate**: Quick
**Commit**: `feat(core): port NottiApiClient, NottiCore and the foreground observer`

---

### T7: NottiAndroidProviders

**What**: Move the RN module readers into `NottiAndroidProviders` (bodies verbatim, design's constructor seams) + `NottiAndroidProvidersTest`.
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiAndroidProviders.kt`, test
**Depends on**: T6
**Reuses**: RN `NottiModule.kt` `readAppVersion` … `fetchFcmToken`
**Requirement**: CORE-06, CORE-07, CORE-08, CORE-11 (SDK-04 token path)

**Done when**:

- [ ] Every reader tested incl. failure → null (cases listed in design)
- [ ] `FirebaseMessaging.getInstance()` throwing → callback `null`, no crash

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(core): move the Android platform providers into the SDK`

---

### T8: NottiRuntime

**What**: `NottiRuntime` per design + `NottiRuntimeTest`; point `NottiForegroundObserver`'s `coreProvider`/`inAppProvider`/`gateProvider` at `NottiRuntime.peek()` (closes the T6 temporary state).
**Where**: `notti/src/main/java/com/zeeplabs/notti/NottiRuntime.kt`, test
**Depends on**: T7
**Requirement**: CORE-02, CORE-03, CORE-04, CORE-05, CORE-14 (golden file name)

**Done when**:

- [ ] Lazy single instance across threads; `stop` identity-guarded
- [ ] Event enqueued with no core is persisted and flushed by a later `start` + registration (MockWebServer)
- [ ] Registration body `sdk_version` == `NottiSdkVersion.CURRENT`
- [ ] Replacing a core shuts the old executor down
- [ ] Production prefs name is `notti_prefs`

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(core): add NottiRuntime as the process container`

---

### T9: Port the foreground and network observers

**What**: Port `NottiNetworkObserver` and the RN tests of both observers (`NottiForegroundObserverTest` 3, `NottiNetworkObserverTest` 2; the foreground observer itself came in T6); RN statics → runtime, In-App → `NottiInAppLifecycle` fake.
**Where**: `NottiForegroundObserver.kt`, `NottiNetworkObserver.kt`, tests
**Depends on**: T8
**Requirement**: CORE-03, CORE-09

**Done when**:

- [ ] 5/5 pass; In-App cases use a fake lifecycle (coordinator version listed as deferred to `in-app-messages` in T14)

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(core): port the foreground and network observers`

---

### T10: Continuity fixture test

**What**: `NottiContinuityTest` with an RN 0.8.0 `notti_prefs` fixture (synthetic values, every key family, correct types).
**Where**: `notti/src/test/java/com/zeeplabs/notti/NottiContinuityTest.kt`
**Depends on**: T9
**Requirement**: CORE-12, CORE-13, CORE-14

**Done when**:

- [ ] Registered-device fixture: no `POST /devices` on start with the same token; device id kept
- [ ] Tags, session counters, pending events, acks, pending logout clear read back
- [ ] Token-changed fixture: behavior equals RN's (re-register path), asserted

**Tests**: unit (Robolectric + MockWebServer)
**Gate**: Quick
**Commit**: `test(core): pin RN 0.8.0 storage continuity`

---

### T11: Log privacy sentinel test

**What**: `NottiLogPrivacyTest`: runtime at `VERBOSE`, capturing sink, flow initialize → token → register → login → setEmail → setPhone → addTags → event flush with failures, all with sentinel values.
**Where**: `notti/src/test/java/com/zeeplabs/notti/NottiLogPrivacyTest.kt`
**Depends on**: T10
**Requirement**: CORE-15, CORE-17

**Done when**:

- [ ] No sentinel (incl. token, base URL with ids) appears in captured output; a deliberately leaking log line makes the test fail (checked once, reverted)

**Tests**: unit
**Gate**: Quick
**Commit**: `test(core): assert no personal data reaches the logs`

---

### T12: Direct tests for RN coverage gaps

**What**: Direct core-layer tests for SDK-04 (null token at registration), SDK-08, SDK-12, SDK-13, SDK-17 where the RN validation had indirect evidence only; each test names the ID; parts owned by other features listed.
**Where**: `notti/src/test/java/com/zeeplabs/notti/NottiCoreGapsTest.kt`
**Depends on**: T11
**Requirement**: CORE-11

**Done when**:

- [ ] One or more tests per ID, or a written reason pointing to the owning feature

**Tests**: unit
**Gate**: Full
**Commit**: `test(core): cover the RN validation gaps directly`

---

### T13: React Native guard and port-diff tool [P]

**What**: `scripts/check-no-react-native.sh` + self-test + CI step in `checks`; dev tools `scripts/port-file.sh` (copy from the RN tag with header, package rename and 2 → 4 reindent that keeps KDoc's odd space) and `scripts/port-diff.sh` (strip header, `@file:Suppress`, package/imports, blank lines; `diff -w`), with a self-test on a throwaway RN-shaped repo.
**Where**: `scripts/`, `.github/workflows/ci.yml`
**Depends on**: None
**Requirement**: CORE-01

**Done when**:

- [x] Self-test: clean tree passes; `import com.facebook.react…`, `ReactApplicationContext`, `WritableMap`, `NativeNottiSpec` each fail; grep status 2 fails
- [x] `actionlint` clean (port tools self-test also green on macOS and Ubuntu 24.04; shellcheck clean)

**Tests**: script self-test
**Gate**: Scripts
**Commit**: `ci: forbid React Native symbols in the SDK`

---

### T14: Traceability

**What**: `.specs/features/core-port/traceability.md`: RN ID → test (or owning feature) for SDK, SDKCTR, SDKOPEN, SEGTEL, DPF, DLOG; test counts per class vs RN; drop/deferred list; storage contract (from T2); `port-diff` summary per ported file; update TESTING.md coverage note.
**Where**: `.specs/features/core-port/traceability.md`
**Depends on**: T12, T13
**Requirement**: CORE-09, CORE-10

**Done when**:

- [ ] No unmapped Android-applicable core ID; counts ≥ RN (257: 208 core + 49 In-App data) plus new tests
- [ ] Every `port-diff` hunk is an allowed change or a listed `SPEC_DEVIATION`
- [ ] Full gate green

**Tests**: none
**Gate**: Full
**Commit**: `docs(specs): core-port traceability`

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
| T9 | T8 | T8 → T9 | ✅ |
| T10 | T9 | T9 → T10 | ✅ |
| T11 | T10 | T10 → T11 | ✅ |
| T12 | T11 | T11 → T12 | ✅ |
| T13 | None | T13 [P] | ✅ |
| T14 | T12, T13 | T12 → T14, T13 → T14 | ✅ |

## Test Co-location Validation

| Task | Layer | Matrix | Tests | OK |
| --- | --- | --- | --- | --- |
| T1, T7, T8 | new classes | unit | unit | ✅ |
| T2, T3, T4, T6, T9 | ported classes | unit 1:1 | unit | ✅ |
| T5 | seams | none | none | ✅ |
| T10, T11, T12 | tests only | unit | unit | ✅ |
| T13 | scripts | self-test | self-test | ✅ |
| T14 | docs | none | none | ✅ |

## Requirement Coverage

| Requirement | Tasks |
| --- | --- |
| CORE-01 | T2, T3, T4, T5, T6, T13 |
| CORE-02 | T4, T5, T6, T8 |
| CORE-03 | T8, T9 |
| CORE-04 | T8 |
| CORE-05 | T8 |
| CORE-06 | T7 |
| CORE-07 | T7 |
| CORE-08 | T7 |
| CORE-09 | T2, T3, T4, T6, T9, T14 |
| CORE-10 | T14 |
| CORE-11 | T7, T12 |
| CORE-12 | T2, T3, T6, T10 |
| CORE-13 | T10 |
| CORE-14 | T8, T10 |
| CORE-15 | T1, T6, T11 |
| CORE-16 | T1 |
| CORE-17 | T11 |

17/17 mapped.
