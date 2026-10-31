# In-App Messages Tasks

**Design**: `.specs/features/in-app-messages/design.md`
**Testing**: `.specs/codebase/TESTING.md`
**Status**: In progress

---

## Execution Protocol (MANDATORY -- do not skip)

`tlc-spec-driven` Execute flow; rules in `AGENTS.md`. One task per commit, tests in the same commit. Port tasks (T1-T6) copy the RN `v0.8.0` file (`git -C ../zeep-notti-react-native show v0.8.0:android/src/...`), add the provenance comment, re-indent to 4 spaces, change package and visibility to `internal`, and change only what the task lists; `scripts/port-diff.sh` must show nothing else. RN tests are ported 1:1 with the same `@Config`/`@GraphicsMode` and the same `@Test` count per class. Changes to already ported or earlier files (`NottiRuntime`, `NottiProcessHooks`, `NottiListeners`, `NottiOptions`, `Notti`, the coordinator in T8) are limited to what the task lists and named in the commit body. Every task that changes public API runs `./gradlew :notti:apiDump` and commits the reviewed dump. Baseline: 444 unit tests; the count never drops. Push/PR only with Julio's OK.

---

## Test Coverage Matrix

| Code Layer | Required Test Type | Location Pattern | Run Command |
| --- | --- | --- | --- |
| Ported pure logic (Session, Style, PushPermissionAction) | unit (JUnit 4) | `notti/src/test/java/com/zeeplabs/notti/<Class>Test.kt` | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` |
| Ported Android views, presenter, image loader, coordinator | unit (Robolectric `sdk = [34]`, `@GraphicsMode(NATIVE)` where RN has it) | same | same |
| Wiring (setup, runtime, observers), URL opener, facade | unit (Robolectric, MockWebServer) | same | same |
| Java interop | unit (Java source) | `NottiJavaInteropTest.java` | same |
| URL guard | script self-test | `scripts/tests/` | `scripts/tests/run.sh` |
| Public ABI | none (build) | `notti/api/notti.api` | `./gradlew :notti:apiCheck` |

## Gate Check Commands

| Gate Level | When | Command |
| --- | --- | --- |
| Quick | each code task | `./gradlew :notti:testDebugUnitTest --tests '<Class>' spotlessCheck :notti:lintRelease` (+ `:notti:apiCheck` when public API changes) |
| Full | T10, T12 | TESTING.md "Full" + `scripts/check-no-url-open.sh` |

---

## Execution Plan

Gradle work is not parallel-safe (TESTING.md), so tasks run in order. T4 is one task because Coordinator, Presenter and ViewFactory reference each other (`InAppDisplayEvents` lives in the presenter file and is implemented by the coordinator; the presenter builds the view factory's layouts; the view factory uses `InAppDismissReason` from the coordinator file).

```
T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 → T11 → T12
```

---

## Task Breakdown

### T1: Port `NottiInAppSession` (+ display clock)

**What**: Port `NottiInAppSession.kt` (session state, `InAppConfig`, routes, fetch state, `InAppDisplay`, `NottiInAppDisplayClock`) and its tests.
**Where**: `NottiInAppSession.kt`, `NottiInAppSessionTest.kt` (both classes)
**Depends on**: None
**Requirement**: INAPP-01, INAPP-02, INAPP-06, INAPP-09

**Done when**:

- [x] `diff -w` vs RN: only package/imports, provenance, `internal`
- [x] 44 + 6 ported tests pass (444 → 494)

**Tests**: unit (JUnit) **Gate**: Quick
**Commit**: `feat(inapp): port the In-App session state`

---

### T2: Port `NottiInAppStyle`

**What**: Port `NottiInAppStyle.kt` and `NottiInAppStyleTest`. (Planned with `NottiInAppAccessibility`; that file reads `InAppMessageViews` from the view factory, so it moved to T4: SPEC_DEVIATION, plan order only.)
**Where**: `NottiInAppStyle.kt`, `NottiInAppStyleTest.kt`
**Depends on**: T1
**Requirement**: INAPP-03

**Done when**:

- [x] Port diff clean; 19 tests pass (→ 513)

**Tests**: unit (JUnit) **Gate**: Quick
**Commit**: `feat(inapp): port the In-App style helpers`

---

### T3: Port `NottiInAppImageLoader`

**What**: Port the loader (dedicated OkHttp client, https-only, 5 MB, downsample, byte LRU, daemon thread) and its tests.
**Where**: `NottiInAppImageLoader.kt`, `NottiInAppImageLoaderTest.kt`
**Depends on**: T2
**Requirement**: INAPP-04

**Done when**:

- [x] Port diff clean; no URL in any log line
- [x] 20 tests pass (→ 533)

**Tests**: unit (Robolectric, `GraphicsMode(NATIVE)`, MockWebServer/okhttp-tls) **Gate**: Quick
**Commit**: `feat(inapp): port the In-App image loader`

---

### T4: Port coordinator, presenter and view factory

**What**: Port `NottiInAppCoordinator.kt` (coordinator, `InAppTiming`, `NottiInAppScheduler`, `NottiInApp` holder, ports), `NottiInAppPresenter.kt`, `NottiInAppViewFactory.kt`, `NottiInAppAccessibility.kt` (moved from T2; RN-public `NottiInAppAccessibility` and `InAppSiblingRestore` become `internal`; no RN test of its own, covered by the ViewFactory/Presenter tests) and their tests. Listed changes: the coordinator's default `clickSink` is `InAppClickSink { false }` until T7 points it at `NottiInAppClicks` (RN pointed at `NottiModule.emitInAppClick`, which returns false with no live module; the ported test `the default click sink reports no listener when no module is live` needs a non-null default); `NottiInAppHooks` stays in its existing file (remove the duplicate declaration from the ported coordinator, D7). No behavior change.
**Where**: the four files + `NottiInAppCoordinatorTest.kt`, `NottiInAppPresenterTest.kt` (both classes), `NottiInAppViewFactoryTest.kt`
**Depends on**: T3
**Requirement**: INAPP-01..06, INAPP-10 (RN order)

**Done when**:

- [x] Port diff shows only the listed changes
- [x] 54 + 22 + 6 + 53 ported tests pass (→ 668)
- [x] Lint findings RN has suppressed per file, not fixed (ViewFactory: `NewApi` (the `DisplayCutout` reads are behind `SDK_INT >= 28` and a null cutout), `RtlHardcoded` (server `left`/`right` alignment), `UseKtx`, `ViewConstructor`; Accessibility: `UseKtx`)

**Tests**: unit (Robolectric) **Gate**: Quick (the three classes, then all tests)
**Commit**: `feat(inapp): port the In-App coordinator, presenter and view factory`

---

### T5: Port `NottiPushPermissionAction`

**What**: Port the action table and `appNotificationSettingsIntent` with `NottiPushPermissionActionTest`.
**Where**: `NottiPushPermissionAction.kt`, `NottiPushPermissionActionTest.kt`
**Depends on**: T4
**Requirement**: INAPP-19

**Done when**:

- [ ] Port diff clean; 9 tests pass (→ 677)

**Tests**: unit (JUnit) **Gate**: Quick
**Commit**: `feat(inapp): port the push-permission button action`

---

### T6: Install the engine with the process hooks (D1)

**What**: Port `NottiInAppSetup.kt` with the listed changes (prefs constant `NottiRuntime.PREFS_NAME`, logger `NottiLogger.warn`, no `t.message`). Call it from `NottiProcessHooks.installOnce` inside its own `try/catch` (error line with the class). `NottiRuntime` passes `NottiInApp.coreHooks` to the core and defaults `inAppLifecycle` to a new `NottiInApp.lifecycle` forwarder. `resetForTest` paths reset `NottiInApp`. Port `NottiInAppEndToEndTest`; add the core-port deferred `NottiForegroundObserverTest` case.
**Where**: `NottiInAppSetup.kt`, `NottiInAppCoordinator.kt` (holder: `lifecycle`), `NottiProcessHooks.kt`, `NottiRuntime.kt`, tests
**Depends on**: T5
**Requirement**: INAPP-01, INAPP-07, INAPP-08

**Done when**:

- [ ] Provider path and lazy `initialize` path both install exactly one coordinator
- [ ] Core `onSessionStarted`/`onRegistered` reach the coordinator; foreground/background/network reach it through the runtime
- [ ] onStop with no live core ends the In-App session
- [ ] Setup throwing does not break the other hooks
- [ ] 3 ported + new tests pass (→ ~683)

**Tests**: unit (Robolectric, MockWebServer) **Gate**: Quick, then all tests
**Commit**: `feat(inapp): install the In-App engine with the process hooks`

---

### T7: Public click types and synchronous delivery (D2, D6)

**What**: `NottiListeners.dispatchNow(value): Boolean`; `NottiInAppClicks` (listeners + `deliver(InAppClick): Boolean`, internal→public mapping); public `NottiInAppClick`, `NottiInAppAction` (+ `Type`), `NottiInAppClickListener`. Setup sets `coordinator.clickSink`.
**Where**: `NottiListeners.kt`, `NottiInAppClicks.kt`, `NottiInAppClick.kt`, `NottiInAppAction.kt`, `NottiInAppClickListener.kt`, `NottiInAppSetup.kt`, tests, `notti.api`
**Depends on**: T6
**Requirement**: INAPP-10, INAPP-12

**Done when**:

- [ ] `dispatchNow` calls entries on the current thread, isolates a throwing one, returns false with none
- [ ] A click reaches every listener before `presenter.close`; payload maps `push_permission` to `action == null`
- [ ] `toString` has no `messageId` or action value
- [ ] `apiDump` reviewed

**Tests**: unit **Gate**: Quick + `apiCheck`
**Commit**: `feat(inapp): deliver In-App clicks to host listeners`

---

### T8: URL opener and guard (D3, D8)

**What**: `NottiInAppUrlOpener` (`accepts`, `open`, `enabled`); coordinator `urlOpener` port and the `onDisplayClicked` block change (open after close, warn rule); Setup wires the opener; `check-no-url-open.sh` allowlist + `Intent.parseUri` pattern + self-tests.
**Where**: `NottiInAppUrlOpener.kt`, `NottiInAppCoordinator.kt` (listed block), `NottiInAppSetup.kt`, `scripts/check-no-url-open.sh`, `scripts/tests/`, `NottiInAppUrlOpenerTest.kt`, `NottiInAppCoordinatorUrlTest.kt`
**Depends on**: T7
**Requirement**: INAPP-11, INAPP-13..16, INAPP-18

**Done when**:

- [ ] Accept table: http/https any case with host opens; `tel:`, `mailto:`, custom, `javascript:`, no host, whitespace/control chars, empty refused
- [ ] Order: listeners → close → one `ACTION_VIEW`; from Activity, else app context + `NEW_TASK`; failure → one fixed warn, no retry
- [ ] `deeplink` never opened; refused → one debug line without value; disabled → nothing
- [ ] Warn only when not delivered, not `push_permission` and not opened
- [ ] `NottiInAppCoordinatorTest` still 54 and unchanged
- [ ] Guard passes on the tree; self-test shows a non-allowlisted `ACTION_VIEW` fails

**Tests**: unit (Robolectric `ShadowApplication`) + script **Gate**: Quick + `scripts/tests/run.sh`
**Commit**: `feat(inapp): open http/https url actions after the listeners (AD-006)`

---

### T9: Facade (`trackEvent`, `InAppMessages`, option, permission port) (D4, D5)

**What**: `Notti.trackEvent`, `Notti.InAppMessages.addClickListener`; `NottiOptions(baseUrl, opensInAppUrls = true)`; `Notti.initialize` order: valid config → `onInitialized` + opener `enabled`, wire the push-permission port, then `start`. Re-express RN `NottiModuleTest` In-App cases.
**Where**: `Notti.kt`, `NottiOptions.kt`, `NottiInAppFacadeTest.kt`, `notti.api`
**Depends on**: T8
**Requirement**: INAPP-09, INAPP-17, INAPP-19, edge cases

**Done when**:

- [ ] `trackEvent` before `initialize` no-op; blank name no-op; any thread
- [ ] Listener added before `initialize` gets later clicks
- [ ] Re-initialize with only the option changed: new value applies, no `POST /devices`
- [ ] Invalid config: `onInitialized` not called (RN)
- [ ] Push-permission port uses the current Activity, `NottiPermissionRequester` through the core, settings intent otherwise
- [ ] `NottiOptions()` and `NottiOptions(String?)` constructors still in the dump; `(String?, Boolean)` added

**Tests**: unit (Robolectric, MockWebServer) **Gate**: Quick + `apiCheck`
**Commit**: `feat(api): add trackEvent, In-App click listeners and opensInAppUrls`

---

### T10: Java interop and log privacy

**What**: Java test calls the new API; `NottiLogPrivacyTest` runs an In-App flow at `VERBOSE` (event name, message id, URL sentinels) and asserts none is logged.
**Where**: `NottiJavaInteropTest.java`, `NottiLogPrivacyTest.kt`
**Depends on**: T9
**Requirement**: Goals (Java, AD-011)

**Done when**:

- [ ] Both pass; Full gate green

**Tests**: unit **Gate**: Full
**Commit**: `test(inapp): cover Java callers and log privacy for In-App`

---

### T11: Docs and example

**What**: README In-App section (`trackEvent`, click listener, `opensInAppUrls` + double-open warning, storage key `notti_pending_inapp_events`, Data safety note unchanged unless new data), CHANGELOG Unreleased; example: a `trackEvent` button and a click listener that logs the button id.
**Where**: `README.md`, `CHANGELOG.md`, `example/`
**Depends on**: T10
**Requirement**: INAPP-20

**Done when**:

- [ ] Example builds against the published AAR

**Tests**: none **Gate**: Full (example step)
**Commit**: `docs: document In-App messages and use them in the example`

---

### T12: Traceability and state

**What**: `traceability.md` (INAPP-01..20 → tests), spec/tasks status, TESTING.md counts, ROADMAP, STATE (close the core-port deferral).
**Where**: `.specs/`
**Depends on**: T11
**Requirement**: all

**Done when**:

- [ ] 20/20 mapped; Full gate green

**Tests**: none **Gate**: Full
**Commit**: `docs(specs): in-app-messages traceability`
