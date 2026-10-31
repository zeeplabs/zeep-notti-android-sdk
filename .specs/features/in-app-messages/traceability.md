# In-App Messages Traceability

**Spec**: `.specs/features/in-app-messages/spec.md`
**Design**: `.specs/features/in-app-messages/design.md`
**Status**: Execute done 2026-10-31 (T1-T12, local branch `feat/in-app-messages`); awaiting review/PR

Test names below are exact (`Class#method`; backtick names as written). Ported RN classes are cited with their count; their 1:1 test names are RN's. `C#` = `NottiInAppCoordinatorTest`, `CU#` = `NottiInAppCoordinatorUrlTest`, `CK#` = `NottiInAppClicksTest`, `UO#` = `NottiInAppUrlOpenerTest`, `IF#` = `NottiInAppFacadeTest`, `E2E#` = `NottiInAppEndToEndTest`, `S#` = `NottiInAppSessionTest`, `FO#` = `NottiForegroundObserverTest`, `H#` = `NottiProcessHooksTest`, `R#` = `NottiRuntimeTest`, `IP#` = `NottiInitProviderTest`, `L#` = `NottiListenersTest`, `T#` = `NottiPublicTypesTest`, `J#` = `NottiJavaInteropTest`, `LP#` = `NottiLogPrivacyTest`.

---

## Requirements

| ID | Requirement | Task(s) | Evidence |
| --- | --- | --- | --- |
| INAPP-01 | One flush + one `eligible` per registered session, RN retry/401/403/408/429/late-response rules | T1, T4, T6 | `C#` (54, RN) incl. `` `queued events are flushed before eligible and the first session_start is presented` ``, `` `eligible is sent once per session across registrations, reloads and track calls` ``, `` `eligible waits for the registration of this process` ``, the 401/403/429/transient cases, `` `a response arriving after its session ended is discarded even with a new session open` ``; `S#` (44, RN); `E2E#` `` `a session runs registration, flush, one eligible, display, impression, click and a held trigger in order` `` |
| INAPP-02 | First `session_start` shown; `event` held until an exact `trackEvent`; one visible, once per session | T1, T4 | `S#` (44); `C#` `` `trackEvent after the response shows the oldest held match` ``, `` `initialize then trackEvent before the first session matches once held messages arrive` ``; `E2E#` (3) |
| INAPP-03 | RN layouts, style, metrics, safe area, swipe, body tap, close control, accessibility | T2, T4 | `NottiInAppViewFactoryTest` (53, RN, `GraphicsMode(NATIVE)`), `NottiInAppStyleTest` (19, RN), `NottiInAppPresenterTest` (22, RN), `NottiInAppPresenterLifecycleTest` (6, RN); `NottiInAppAccessibility` covered through them (no RN test of its own) |
| INAPP-04 | Image: dedicated client, https only, 10 s, 5 MB, downsampled, LRU by bytes; message shows without it on failure | T3 | `NottiInAppImageLoaderTest` (20, RN, MockWebServer + okhttp-tls) |
| INAPP-05 | impression / click / dismiss queued with stable UUID, sent in order, 7 days, 100 max | T4 (store from core-port) | `C#` `` `impression then click are sent in order and the click reaches the sink` ``, `` `events transient retries the same head with the same event_id` ``, `` `events older than 7 days are never sent` ``, `` `repeated visible callbacks enqueue one impression` ``; `NottiInAppEventStoreTest` (13, core-port) |
| INAPP-06 | `dismiss_after_seconds` with the clock paused in background or detached | T1, T4 | `NottiInAppDisplayClockTest` (6, RN); `C#` `` `auto dismiss fires after the visible time and pauses in background` ``; `NottiInAppPresenterLifecycleTest` (6) |
| INAPP-07 | Background, foreground, network through `NottiInAppLifecycle` | T6 | `FO#` `` `onStop with no live core still ends the In-App session` `` (RN's coordinator-backed version, through the runtime's default provider); `R#theInAppLifecycleForwardsToTheInstalledCoordinator`, `R#theCoreSessionStartReachesTheInstalledInAppCoordinator`, `R#observersReachTheRuntimeThroughPeek`; `C#` `` `eligible transient failures back off 2 4 8 16 then give up, resumed on network` `` |
| INAPP-08 | Works when the host removed `NottiInitProvider` | T6 | `H#twoInstallsRegisterEachHookOnce` (the `initialize` path installs one coordinator), `H#installAfterTheProviderIsANoOp` (same coordinator), `H#aFailingInAppSetupDoesNotBreakTheOtherHooks`, `IP#aThrowingStepDoesNotStopTheOthersNorCrash` (the In-App step fails alone) |
| INAPP-09 | `Notti.trackEvent` any thread; early names kept (32); blank / before initialize no-op; name never logged | T9, T10 | `IF#trackEventForwardsTheNameSoACallRightAfterInitializeIsKept` (background thread, blank names), `IF#trackEventWithNoInAppEngineIsANoOp`; `C#` `` `trackEvent before initialize or blank is a no-op` ``; `S#` (pending names cap); `LP#inAppFlowAtVerboseLogsNoSentinel` |
| INAPP-10 | Every listener gets one `NottiInAppClick` on main, in order, before the close; a throwing one isolated | T7 | `CK#everyListenerGetsTheClickBeforeTheMessageCloses`, `CK#aThrowingListenerDoesNotStopTheOthersNorTheClose`, `L#dispatchNowCallsEveryListenerOnTheCallingThreadAndPostsNothing`; `C#` `` `a click is enqueued before it is delivered and delivered before the display closes` ``; `IF#aClickListenerAddedBeforeInitializeGetsTheExactPayloadUntilRemoved` (payload byte for byte), `IF#aClickWithoutAUsableActionCarriesActionNull`; `T#inAppToPublicMapsUrlAndDeeplinkAndDropsPushPermission` |
| INAPP-11 | Warn only when no listener, no URL opened, not `push_permission` | T7, T8 | `CK#aRemovedListenerGetsNothingAndTheUnhandledClickIsWarned`, `CU#anOpenedUrlWithNoListenerIsHandledAndNotWarned`, `CU#aRefusedUrlIsNotOpenedAndWarnedWithNoListener`, `CU#aDeeplinkNeverReachesTheOpener`, `CU#aPushPermissionTapNeverReachesTheOpener`, `CU#withNoOpenerAUrlBehavesAsInReactNative`; `C#` `` `click with no listener is still reported, closed and warned` `` |
| INAPP-12 | Removed listener gets nothing; `remove()` idempotent | T7, T9 | `CK#aRemovedListenerGetsNothingAndTheUnhandledClickIsWarned`, `L#dispatchNowReportsNoDeliveryWithNoLiveListener`, `IF#aClickListenerAddedBeforeInitializeGetsTheExactPayloadUntilRemoved` |
| INAPP-13 | Accepted http/https `url` opened once with `ACTION_VIEW` after listeners and close | T8 | `CU#anAcceptedUrlOpensOnceAfterTheListenersAndTheClose`, `UO#opensFromTheResumedActivity` (`CATEGORY_BROWSABLE`, no `NEW_TASK`), `UO#opensFromTheApplicationInANewTaskWithNoActivity`, `UO#theSetupWiresTheOpenerIntoTheCoordinator` |
| INAPP-14 | Not accepted or `deeplink`: not opened, listeners untouched, at most one debug line without the value | T8 | `UO#acceptsHttpAndHttpsWithAHostOnly` (`tel:`, `mailto:`, custom, `javascript:`, `intent:`, no host, whitespace, control chars, NBSP, empty), `UO#willOpenLogsOneFixedDebugLineForARefusedValueAndNothingWhileDisabled`, `CU#aDeeplinkNeverReachesTheOpener`, `CU#aRefusedUrlIsNotOpenedAndWarnedWithNoListener` |
| INAPP-15 | Option off: nothing opened, nothing logged | T8, T9 | `CU#aDisabledOpenerOpensNothing`, `UO#willOpenLogsOneFixedDebugLineForARefusedValueAndNothingWhileDisabled` |
| INAPP-16 | Failed open: one fixed warn line without the URL, no retry | T8, T10 | `UO#aFailedOpenLogsOneFixedWarnLineWithoutTheUrl`, `LP#inAppFlowAtVerboseLogsNoSentinel` |
| INAPP-17 | Re-initialize with only the option changed applies it, no new registration | T9 | `IF#reinitializingWithOnlyTheUrlOptionChangedAppliesItWithoutANewRegistration` (also: an invalid call changes nothing, `NottiOptions()` turns it back on); `T#optionsOpenInAppUrlsByDefaultAndCompareByEveryField` |
| INAPP-18 | URL-open call outside the opener fails CI | T8 | `scripts/check-no-url-open.sh` (allowlist = `NottiInAppUrlOpener.kt`, pattern + `Intent\.parseUri`); `scripts/tests/check-no-url-open-test.sh`: "the URL opener is allowlisted", "ACTION_VIEW next to the opener fails", "Intent.parseUri fails", "the repository tree passes" |
| INAPP-19 | `push_permission`: listener gets `action == null`, no warn, close, then prompt / settings / nothing | T4, T5, T9 | `NottiPushPermissionActionTest` (7, RN) + `NottiAppNotificationSettingsIntentTest` (2, RN); `C#` `` `a push permission tap reports one click, closes, tells the listener null and runs the port once` ``; `CK#aPushPermissionTapReachesTheListenerWithNoAction`; `IF#initializeWiresThePushPermissionPortOnEveryCall`, `IF#thePushPermissionActionWithoutAnActivityShowsNoPromptAndLeavesTheAskedFlagUnset` (API 33), `IF#thePushPermissionActionOpensTheSettingsFromTheCurrentActivityOnceAsked` |
| INAPP-20 | README: trackEvent, listener, option + double-open warning, storage key, data | T11 | README "In-App messages" (links warning, `trackEvent`, listener, web links, `push_permission`, events + `notti_pending_inapp_events`, presentation, data sent), Java snippet; CHANGELOG Unreleased |

## Goals

| Goal | Evidence |
| --- | --- |
| RN parity, ported tests 1:1 | 236 RN tests in 11 classes, counts equal to RN (table below); `scripts/port-diff.sh` shows only the listed changes per file |
| Public surface usable from Java, guarded by `apiCheck` | `notti/api/notti.api` (`Notti.trackEvent`, `Notti$InAppMessages`, `NottiInAppClick`, `NottiInAppAction` + `Type`, `NottiInAppClickListener`, `NottiOptions(String, boolean)`); `J#everyPublicMethodIsCallableFromJava` |
| No PII in logs (AD-011) | `LP#inAppFlowAtVerboseLogsNoSentinel` (event name, message id, URL host and query, exception message, server body); `T#inAppClickToStringHidesTheMessageIdAndTheLink`; `C#` `` `requests are only in-app POSTs and logs carry no identifiers` ``, `` `identity and config never print their secrets` `` |
| Works with and without `NottiInitProvider` | INAPP-08 |

## Initialize order (design D5)

| Check | Evidence |
| --- | --- |
| In-App gets the normalized config before the core exists | `IF#initializeHandsTheNormalizedConfigToInAppBeforeTheCoreIsCreated` |
| Invalid config never reaches In-App | `IF#initializeWithAnInvalidConfigLeavesInAppWithoutOne` |
| The runtime's core opens the In-App session | `IF#theCoreOpensTheInAppSessionWhenItOpensAForegroundSession` |

## Test counts

| Class | Count | RN | Note |
| --- | --- | --- | --- |
| `NottiInAppSessionTest` | 44 | 44 | ported (T1) |
| `NottiInAppDisplayClockTest` | 6 | 6 | ported (T1) |
| `NottiInAppStyleTest` | 19 | 19 | ported (T2) |
| `NottiInAppImageLoaderTest` | 20 | 20 | ported (T3) |
| `NottiInAppCoordinatorTest` | 54 | 54 | ported (T4), unchanged by T8 |
| `NottiInAppPresenterTest` | 22 | 22 | ported (T4) |
| `NottiInAppPresenterLifecycleTest` | 6 | 6 | ported (T4) |
| `NottiInAppViewFactoryTest` | 53 | 53 | ported (T4) |
| `NottiPushPermissionActionTest` | 7 | 7 | ported (T5) |
| `NottiAppNotificationSettingsIntentTest` | 2 | 2 | ported (T5) |
| `NottiInAppEndToEndTest` | 3 | 3 | ported (T6) |
| `NottiInAppClicksTest` | 4 | - | new (T7) |
| `NottiInAppUrlOpenerTest` | 6 | - | new (T8) |
| `NottiInAppCoordinatorUrlTest` | 7 | - | new (T8) |
| `NottiInAppFacadeTest` | 11 | - | new (T9), RN `NottiModuleTest` In-App cases re-expressed |
| Additions to existing classes | 10 | - | `L#` +2, `T#` +4, `H#` +1, `R#` +2, `LP#` +1; `FO#` 3 → 3 (RN's version restored); `J#` 1 → 1 (more calls) |
| **Suite** | **718** | | 444 before the feature (+236 ported, +38 new) |

RN `NottiModuleTest` cases tied to the RN module lifecycle (`invalidate` removing its own port, `invalidate` keeping the coordinator, emit to a live module, no replay to a later module) have no counterpart: the native SDK has no module instance. Their intent is covered by `IF#initializeWiresThePushPermissionPortOnEveryCall` and the process-wide `NottiInAppClicks` (`CK#`).

## Changes to ported files (beyond package, provenance, `internal`, per-file suppressions)

| File | Change | Task |
| --- | --- | --- |
| `NottiInAppCoordinator.kt` | duplicate `NottiInAppHooks` dropped (kept in its own file); default `clickSink` → `NottiInAppClicks`; `urlOpener` port and the `onDisplayClicked` URL block; `NottiInApp.lifecycle` forwarder | T4, T6, T7, T8 |
| `NottiInAppSetup.kt` | prefs `NottiRuntime.PREFS_NAME`; logger `NottiLogger.warn`; wires `NottiInAppUrlOpener` | T6, T8 |
| `NottiInAppViewFactory.kt` | lint `NewApi` (guarded cutout reads), `RtlHardcoded`, `UseKtx`, `ViewConstructor` suppressed | T4 |
| `NottiInAppAccessibility.kt` | lint `UseKtx` suppressed; moved from T2 to T4 (needs the view factory) | T4 |
| `NottiPushPermissionAction.kt` | lint `InlinedApi` suppressed | T5 |

## Deviations from the plan

- T2 → T4: `NottiInAppAccessibility` reads `InAppMessageViews` from the view factory.
- D2: the coordinator's default `clickSink` points at `NottiInAppClicks` (RN shape) instead of Setup assigning it; T4 shipped `{ false }` until T7 so the ported default-sink test kept passing.
- D3: the port carries `enabled` and `willOpen`; the facade reaches the option through `NottiInApp.get()?.urlOpener`.
