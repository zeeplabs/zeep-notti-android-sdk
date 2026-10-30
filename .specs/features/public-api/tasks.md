# Public API Tasks

**Design**: `.specs/features/public-api/design.md`
**Testing**: `.specs/codebase/TESTING.md`
**Status**: Execute (approved by Julio 2026-10-30; T1-T4 done)

---

## Execution Protocol (MANDATORY -- do not skip)

`tlc-spec-driven` Execute flow; rules in `AGENTS.md`. One task per commit, tests in the same commit. Changes to ported files (`NottiRuntime`, `NottiInitProvider`, `NottiActivityLifecycleListener`) are limited to what the task lists and named in the commit body. Every task that changes public API runs `./gradlew :notti:apiDump` and commits the reviewed dump. Baseline: 390 unit tests; the count never drops. Push/PR only with Julio's OK.

---

## Test Coverage Matrix

| Code Layer | Required Test Type | Location Pattern | Run Command |
| --- | --- | --- | --- |
| Runtime, hooks, lifecycle listener (changed internals) | unit (Robolectric) | `notti/src/test/java/com/zeeplabs/notti/<Class>Test.kt` | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` |
| New internals (`NottiListeners`, `NottiProcessHooks`) | unit (Robolectric main looper) | same | same |
| Public types and facade | unit (Robolectric, MockWebServer on an isolated runtime) | same | same |
| Java interop | unit (Java source in the test set) | `NottiJavaInteropTest.java` | same |
| Public ABI | none (build) | `notti/api/notti.api` | `./gradlew :notti:apiCheck` |
| README, example, specs | none | - | Full gate |

## Gate Check Commands

| Gate Level | When | Command |
| --- | --- | --- |
| Quick | each code task | `./gradlew :notti:testDebugUnitTest --tests '<Class>' spotlessCheck` (+ `:notti:apiCheck` when the task touches public API) |
| Full | T10, T11 | TESTING.md "Full" |

---

## Execution Plan

Gradle work is not parallel-safe (TESTING.md), so tasks run in order.

```
T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 → T11
```

---

## Task Breakdown

### T1: Runtime `ensureCore` and runtime-level hooks

**What**: Add `NottiRuntime.ensureCore()` (synchronized, builds the core without `initialize`, `onCoreCreated` once per core); rebuild `start` as `ensureCore().also { it.initialize(...) }`; move `permissionRequester` from `start` params to the runtime; add the `onDeviceIdChanged: ((String) -> Unit)?` slot read by the core callback (D1, D3).
**Where**: `NottiRuntime.kt`, `NottiRuntimeTest.kt`, callers of `start` in tests
**Depends on**: None
**Requirement**: API-02, API-05

**Done when**:

- [x] `ensureCore` twice returns the same core; no `initialize`, no `POST /devices`
- [x] Second `start` with the same config returns the same core and sends no registration; a different config sends one (test rewritten from "replaces the core", count kept)
- [x] Device-id change reaches the runtime slot; null slot is safe
- [x] Two concurrent `start` calls leave one core, no leaked executor
- [x] All tests pass (390 → 393)

**Tests**: unit (Robolectric)
**Gate**: Quick (`NottiRuntimeTest`, then all tests since callers change)
**Commit**: `refactor(runtime): build the core lazily and re-initialize it in place`

---

### T2: Extract `NottiProcessHooks.installOnce`

**What**: Move the `NottiInitProvider.onCreate` body (lifecycle listener, runtime seed, network observer, foreground observer posted to main) to an internal `NottiProcessHooks` guarded by an `AtomicBoolean`; the provider calls it (D2).
**Where**: `NottiProcessHooks.kt` (new), `NottiInitProvider.kt`, `NottiProcessHooksTest.kt` (new)
**Depends on**: T1
**Requirement**: API-13

**Done when**:

- [x] Two `installOnce` calls register each hook once
- [x] `NottiInitProviderTest` green unchanged
- [x] Provider keeps its provenance header; commit body lists the relocation as the only change

**Tests**: unit (Robolectric)
**Gate**: Quick (`NottiProcessHooksTest`, `NottiInitProviderTest`)
**Commit**: `refactor(hooks): move process hooks out of the init provider`

---

### T3: Skip Recents reopen on the resumed path

**What**: In `NottiActivityLifecycleListener`, the `onActivityResumed` path skips an intent with `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` (D2, spec cold-start edge case).
**Where**: `NottiActivityLifecycleListener.kt`, `NottiActivityLifecycleListenerTest.kt` (new cases only; the 20 ported cases untouched)
**Depends on**: T2
**Requirement**: API-13

**Done when**:

- [x] Late install (no `onActivityCreated`): a tap intent on resume emits `opened` + `clicked` once
- [x] Same with `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`: nothing emitted
- [x] Ported 20 cases green; `port-diff` shows only the added check

**Tests**: unit (Robolectric)
**Gate**: Quick (`NottiActivityLifecycleListenerTest`)
**Commit**: `fix(push): skip Recents reopens when a tap is handled on resume`

---

### T4: Public value types and listener interfaces

**What**: Add `NottiOptions`, `NottiNotification` (+ internal `ParsedNotification.toPublic()`), `NottiLogLevel` (+ internal mapping to `NottiLogger.Level`), `NottiSubscription`, `NottiNotificationListener`, `NottiDeviceIdListener`, `NottiPermissionCallback` (D5).
**Where**: one file per public type under `com.zeeplabs.notti`, `NottiPublicTypesTest.kt` (new), `notti/api/notti.api`
**Depends on**: T3
**Requirement**: API-10, API-12, API-14, API-15

**Done when**:

- [x] `NottiNotification.toString` prints `data` keys only (no values, no title/body text); `equals`/`hashCode` by value
- [x] `toPublic` keeps `data` exactly
- [x] `NottiLogLevel` maps all six values both ways
- [x] `NottiOptions()` has `baseUrl == null`
- [x] Dump lists only these types plus `NottiBridge`; no internal type in a signature

**Tests**: unit
**Gate**: Quick + `apiCheck`
**Commit**: `feat(api): add the public value types and listener interfaces`

---

### T5: `NottiListeners` fan-out

**What**: Internal `NottiListeners<T>` with `add(listener): NottiSubscription`, `dispatch(value)`, `onFirstAdded`/`onLastRemoved`, main-`Handler` posting, per-listener `try/catch`; idempotent `NottiSubscription.remove()` that drops its listener reference (D3).
**Where**: `NottiListeners.kt` (new), `NottiSubscription.kt`, `NottiListenersTest.kt` (new)
**Depends on**: T4
**Requirement**: API-10, API-11

**Done when**:

- [ ] Two listeners get every event, in order, on the main looper, also when dispatched from a background thread
- [ ] `remove()` stops delivery; second `remove()` no-op; removed subscription holds no listener
- [ ] Add/remove inside a callback: no throw, applies from the next event
- [ ] Throwing listener: others still called; the log line has the class name, not the message
- [ ] `onFirstAdded` once on 0→1, `onLastRemoved` once on 1→0

**Tests**: unit (Robolectric)
**Gate**: Quick
**Commit**: `feat(api): add listener fan-out with main-thread delivery`

---

### T6: `Notti` initialize, identity and log level

**What**: `object Notti` with `initialize(context, appId, clientKey, options)`, `login`, `logout`, `deviceId`, `addDeviceIdChangedListener`, `logLevel`, plus `runtimeOrWarn()`; `initialize` calls `NottiProcessHooks.installOnce` and keeps only `applicationContext` (D2, facade map).
**Where**: `Notti.kt` (new), `NottiFacadeTest.kt` (new), `notti/api/notti.api`
**Depends on**: T5
**Requirement**: API-01, API-02, API-03, API-04, API-05, API-13, API-15

**Done when**:

- [ ] Default `baseUrl` used when `options.baseUrl` is null; one `POST /devices`
- [ ] Blank `appId`/`clientKey` or invalid `baseUrl`: no throw, no registration, an initialized core keeps its config
- [ ] Same config again: 0 registrations; different config: 1
- [ ] Facade path reads/writes `notti_prefs`
- [ ] `login`/`logout` reach the core (PATCH asserted); before initialize they follow the core's pending queue; with no runtime: warn line with the method name only
- [ ] `deviceId` before initialize returns the persisted id or null; device-id listeners called once on main per change, not on same-id re-registration
- [ ] `initialize` from a background thread equals a main-thread call; an Activity context is not retained
- [ ] Without the provider, `initialize` installs hooks once; with it, none again
- [ ] `logLevel` set/get maps to `NottiLogger.level`; default `ERROR`

**Tests**: unit (Robolectric, MockWebServer)
**Gate**: Quick + `apiCheck`
**Commit**: `feat(api): add Notti.initialize, identity and log level`

---

### T7: `Notti.User` and profile calls

**What**: `object Notti.User` (`addTag`, `addTags`, `removeTag`, `removeTags`, `setEmail`, `clearEmail`, `setPhone`, `clearPhone`) and top-level `setSubscription`, `setLocationSharingEnabled`, all through `ensureCore()`.
**Where**: `Notti.kt`, `NottiFacadeTest.kt` (or `NottiUserFacadeTest.kt` if the class grows past ~400 lines), `notti/api/notti.api`
**Depends on**: T6
**Requirement**: API-06, API-07, API-08

**Done when**:

- [ ] One test per method after initialize asserting the PATCH body fields
- [ ] One test per method before initialize: no throw, persisted, converges at registration
- [ ] `setLocationSharingEnabled` writes on the caller's thread; default `false`
- [ ] `setSubscription(false)` keeps the `last_unsubscribed_at` rule

**Tests**: unit (Robolectric, MockWebServer)
**Gate**: Quick + `apiCheck`
**Commit**: `feat(api): add Notti.User and profile calls`

---

### T8: `Notti.Notifications` events and permission

**What**: `object Notti.Notifications` with `addReceivedListener`, `addClickedListener` (relay attach on first, detach on last), `takeInitialClick`, `requestPermission(callback)` posted to main (D3, D4).
**Where**: `Notti.kt`, `NottiNotificationsFacadeTest.kt` (new), `NottiPermissionFacadeTest.kt` (new), `notti/api/notti.api`
**Depends on**: T7
**Requirement**: API-09, API-10, API-11, API-12

**Done when**:

- [ ] Received: two listeners each get a delivered payload (`deliverRemoteMessage` with synthetic `fixture` data) as `NottiNotification`; removed one stops
- [ ] Listener added before initialize works once events flow
- [ ] Clicked: with a listener the click is delivered, not buffered; after the last remove, a click is buffered and `takeInitialClick()` returns it once, then null
- [ ] `requestPermission`: callback once on main; before initialize `false` with no dialog; two concurrent calls each called once

**Tests**: unit (Robolectric)
**Gate**: Quick + `apiCheck`
**Commit**: `feat(api): add Notti.Notifications events and permission`

---

### T9: Java interop and privacy at VERBOSE

**What**: `NottiJavaInteropTest.java` calling every public method with Java lambdas; new `NottiLogPrivacyTest` case running a facade flow at `VERBOSE` with sentinel values.
**Where**: `notti/src/test/java/com/zeeplabs/notti/NottiJavaInteropTest.java` (new), `NottiLogPrivacyTest.kt`
**Depends on**: T8
**Requirement**: API-14, API-15

**Done when**:

- [ ] Java test compiles and runs in `testDebugUnitTest` (`Notti.User.addTag`, `new NottiOptions()`, `Notti.setLogLevel`, lambdas for the three interfaces)
- [ ] No sentinel (email, phone, token, external id, country, tag value, URL) reaches the sink at `VERBOSE`

**Tests**: unit
**Gate**: Quick
**Commit**: `test(api): cover Java callers and log privacy at verbose`

---

### T10: README, example and CHANGELOG

**What**: README "Usage" section (initialize, user, notifications, listeners, log level, Java snippet); update "Removing the process-start hook" with the three cold-start cases and the process-restore limitation; example `MainActivity` uses `Notti`; CHANGELOG `Unreleased` entry.
**Where**: `README.md`, `example/src/main/java/com/zeeplabs/notti/example/MainActivity.kt` (+ an `Application` if needed for `initialize`), `CHANGELOG.md`
**Depends on**: T9
**Requirement**: API-13, API-14

**Done when**:

- [ ] README snippets match the dump (names and signatures)
- [ ] No PII or real keys in snippets (`example.invalid`, placeholders)
- [ ] Example builds against the published AAR
- [ ] Full gate green

**Tests**: none
**Gate**: Full
**Commit**: `docs: document the public API and use it in the example`

---

### T11: Traceability and state

**What**: `traceability.md` (each API-xx and each RN 0.8.0 public method outside In-App → test), TESTING.md coverage count, spec traceability statuses, ROADMAP/STATE updates.
**Where**: `.specs/features/public-api/{traceability,spec,tasks}.md`, `.specs/codebase/TESTING.md`, `.specs/project/{ROADMAP,STATE}.md`
**Depends on**: T10
**Requirement**: all

**Done when**:

- [ ] 15/15 requirements mapped to tests; test count ≥ 390 recorded
- [ ] Full gate green

**Tests**: none
**Gate**: Full
**Commit**: `docs(specs): public-api traceability`

---

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
| T10 | T9 | T9 → T10 | ✅ |
| T11 | T10 | T10 → T11 | ✅ |

T4 and T5 have no code dependency on T3, but Gradle is not parallel-safe, so the chain stays linear.

## Test Co-location Validation

| Task | Layer | Matrix requires | Task Tests | OK |
| --- | --- | --- | --- | --- |
| T1 | Runtime | unit (Robolectric) | unit (Robolectric) | ✅ |
| T2 | Hooks | unit (Robolectric) | unit (Robolectric) | ✅ |
| T3 | Lifecycle listener | unit (Robolectric) | unit (Robolectric) | ✅ |
| T4 | Public types + ABI | unit + apiCheck | unit + apiCheck | ✅ |
| T5 | `NottiListeners` | unit (Robolectric) | unit (Robolectric) | ✅ |
| T6 | Facade | unit (Robolectric, MockWebServer) + apiCheck | same | ✅ |
| T7 | Facade | same | same | ✅ |
| T8 | Facade | unit (Robolectric) + apiCheck | same | ✅ |
| T9 | Java interop | unit (Java) | unit | ✅ |
| T10 | Docs/example | none | none + Full gate | ✅ |
| T11 | Specs | none | none + Full gate | ✅ |

## Requirement Coverage

| Requirement | Tasks |
| --- | --- |
| API-01 | T6 |
| API-02 | T1, T6 |
| API-03 | T6 |
| API-04 | T6 |
| API-05 | T1, T6 |
| API-06 | T7 |
| API-07 | T7 |
| API-08 | T7 |
| API-09 | T8 |
| API-10 | T4, T5, T8 |
| API-11 | T5, T8 |
| API-12 | T4, T8 |
| API-13 | T2, T3, T6, T10 |
| API-14 | T4, T9, T10 |
| API-15 | T4, T6, T9 |

15/15 mapped.
