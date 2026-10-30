# Push Integration Traceability

**Spec**: `.specs/features/push-integration/spec.md`
**Design**: `.specs/features/push-integration/design.md`
**Status**: Execute done 2026-10-29 (T1-T10, local branch `feat/push-integration`); awaiting review/PR

Test names below are exact (`Class#method`; backtick names as written). `FS#` = `NottiFirebaseMessagingServiceTest`, `FSR#` = `NottiFirebaseMessagingServiceRuntimeTest`, `CR#` = `NottiNotificationClickRelayTest`, `AL#` = `NottiActivityLifecycleListenerTest`, `CA#` = `NottiCurrentActivityTest`, `PR#` = `NottiPermissionRequesterTest`, `R#` = `NottiRuntimeTest`, `IP#` = `NottiInitProviderTest`, `B#` = `NottiBridgeTest`, `BR#` = `NottiBridgeRuntimeTest`, `M#` = `NottiManifestTest`, `G#` = `NottiCoreGapsTest`. "→ feature" = owned by that feature.

---

## PUSH requirements

| ID | Requirement | Task(s) | Evidence |
| --- | --- | --- | --- |
| PUSH-01 | Manifest service, not exported, `POST_NOTIFICATIONS` | T6, T8 | `M#fcmServiceHandlesMessagingEventAndIsNotExported`, `M#postNotificationsIsRequested`; CI "Minified example keeps the SDK components (PUSH-01)" (R8 keeps the service, provider and permission Activity with no keep rules) |
| PUSH-02 | Token refresh to the live core, dropped with none | T1 | `FSR#tokenReachesTheLiveCore`, `FSR#tokenWithNoRuntimeIsDroppedWithoutCreatingOne` |
| PUSH-03 | Parse, received relay, received event | T1 | FS#`` `parseRemoteMessage extracts title, body, and custom data` ``, FS#`` `parseRemoteMessage handles a data-only message with no notification block` ``, FS#`` `parseRemoteMessage handles an empty message with no notification and no data` ``, FS#`` `onMessageReceived enqueues a received event when notification_id and delivery_id are present` ``, `FSR#messageWithNoRuntimeCreatesItAndPersistsTheReceivedEvent`, `FSR#messageIsHandedToTheReceivedCallbackParsed`, `FSR#resetForTestClearsTheReceivedCallback` |
| PUSH-04 | No event without both ids | T1 | FS#`` `onMessageReceived skips enqueue when the SDK ids are absent` ``, FS#`` `onMessageReceived skips enqueue when only one of the SDK ids is present` `` |
| PUSH-05 | Registrations at process start | T6 | `IP#createsTheRuntimeAndRegistersTheHooksWithNoNetworkRequest` (runtime, network callback, foreground observer only after the main-looper post), `IP#registersTheLifecycleListenerOnce`, AL#`` `registerOnce installs a single listener no matter how many times it runs` `` |
| PUSH-06 | No I/O beyond construction; failures logged, no crash | T6 | `IP#createsTheRuntimeAndRegistersTheHooksWithNoNetworkRequest` (MockWebServer receives nothing, nothing logged), `IP#aThrowingStepDoesNotStopTheOthersNorCrash` (three steps fail, logged by class, the post still runs), `IP#noApplicationContextReturnsFalseAndLogs` |
| PUSH-07 | Provider not exported, authority distinct from RN's | T6, T8 | `M#initProviderHasItsOwnAuthorityAndIsNotExported` (`${applicationId}.com.zeeplabs.notti.initprovider`, not `${applicationId}.nottiinitprovider`, `initOrder` 100) |
| PUSH-08 | Removed provider documented; lazy runtime still works | T10 | README "Android integration" (draft; final in `example-and-release`): lists every hook lost when the provider is removed (tap detection, foreground/background tracking, reconnect flush), which is more than the spec's two examples because nothing else installs the hooks yet; lazy runtime: `FSR#messageWithNoRuntimeCreatesItAndPersistsTheReceivedEvent`, `R#getIsLazySingleInstanceAcrossThreads`, `R#enqueueWithoutCorePersists` |
| PUSH-09 | Parse and handle each intent once | T3 | AL#`` `parseClickIntentExtras extracts the payload when google message_id is present` ``, AL#`` `parseClickIntentExtras keeps FCM's analytics labels out of the integrator's data` ``, AL#`` `parseClickIntentExtras falls back to data title and body when gcm n title and gcm n body are absent` ``, AL#`` `parseClickIntentExtras prefers gcm n title and gcm n body over data when both are present` ``, AL#`` `parseClickIntentExtras returns null for a null intent or one with no extras at all` ``, AL#`` `parseClickIntentExtras returns null on an unrelated launch even with other extras present` ``, AL#`` `parseClickIntentExtras skips a malformed null-valued extra without crashing` ``, AL#`` `the same click intent handled twice emits once and leaves the host app's extras intact` ``, AL#`` `onActivityCreated with a non-null savedInstanceState does not replay the click as a new event` ``, AL#`` `onActivityCreated with FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY does not replay the click as a new event` ``, AL#`` `a real cold-start tap with no saved state and no history flag is still delivered via onActivityCreated` `` |
| PUSH-10 | Click relay buffer; opened + clicked | T2, T3 | `CR#` (6); AL#`` `a cold-start click detected before any module exists is held for the JS pull, not replayed as an event` ``, AL#`` `a body tap carrying the SDK ids enqueues exactly one opened and one clicked event` ``, AL#`` `a cold-start body tap enqueues one opened and one clicked event and still buffers the click for JS` ``, AL#`` `a clicked notification without the SDK ids is not enqueued` `` |
| PUSH-11 | Created/resumed dedupe; new intent re-reports | T3 | AL#`` `the same tap seen by onActivityCreated and onActivityResumed enqueues a single opened and clicked pair` ``, AL#`` `a second warm tap delivering a new intent enqueues a second opened and clicked pair` ``, AL#`` `a second tap delivering a new intent to the same activity emits again` `` |
| PUSH-12 | Data untouched, no URL opened (CI guard) | T3, T9 | AL#`` `a body tap whose data carries a URL enqueues the same opened and clicked pair` ``, AL#`` `the same click intent handled twice emits once and leaves the host app's extras intact` ``; `scripts/check-no-url-open.sh` + `scripts/tests/check-no-url-open-test.sh` (CI `checks`) |
| PUSH-13 | Bridge: same results, safe no-ops | T7 | B#`` `onMessageReceived is a safe no-op when no module is active` ``, B#`` `onNewToken reaches the active core without throwing` ``, `BR#messageWithNoRuntimeIsANoOpThatCreatesNothing`, `BR#tokenWithNoRuntimeIsANoOpThatCreatesNothing`, `BR#messageWithARuntimeAndNoCoreMatchesTheService`, `BR#messageWithoutBothIdsEnqueuesNothing` |
| PUSH-14 | Bridge public, Java-callable, in the ABI dump | T7 | `BR#methodsAreStaticForJavaCallers`; `notti/api/notti.api` (exactly `NottiBridge`, `INSTANCE`, `onMessageReceived`, `onNewToken`); `:notti:apiCheck` |
| PUSH-15 | Service removable from the manifest | T8 | `:example-manual-fcm` (`tools:node="remove"` + `ManualFcmService` → `NottiBridge`), built from the published AAR; CI "Manual FCM merged manifest (PUSH-15)" |
| PUSH-16 | API < 33 resolves true | T4 | PR#`` `requestPermission on Android below 13 resolves granted without ever touching the current activity` `` (`@Config(sdk = [32])`) |
| PUSH-17 | API ≥ 33 dialog, flag, single resolution | T4 | `PR#grantedAnswerResolvesTrueOnceAndFinishes`, `PR#deniedAnswerResolvesFalse`, `PR#flagIsSetBeforeTheDialogIsAnswered`, `PR#twoConcurrentRequestsEachResolveOnceWithTheirOwnAnswer`, `PR#startActivityFailureResolvesFalse`, `PR#recreatedWhileTheDialogIsUpDoesNotAskAgainAndStillResolves`, `PR#anIdNoLongerPendingFinishesWithoutAsking`, `PR#noIdFinishesWithoutAsking`, `PR#theStartedIntentHasNoAnimation`; `M#permissionActivityIsTranslucentHiddenAndNotExported` |
| PUSH-18 | No Activity resolves false | T4 | `PR#noActivityResolvesFalseWithoutSettingTheFlag`; runtime default with no Activity: `G#sdk08DefaultRuntimeRequesterDeniesWithoutPromptingUntilPushIntegration` |
| PUSH-19 | Runtime wires requester and Activity tracker | T3, T5 | `CA#` (7); `R#permissionStatusIsSentOnApi34WithAResumedActivity`, `R#defaultPermissionRequesterShowsTheSdkDialogFromTheCurrentActivity` |

19/19 mapped.

---

## RN requirement IDs owned here

Routed to push-integration by `core-port/traceability.md`.

| ID | Behavior | Tests here / owner |
| --- | --- | --- |
| SDK-04 | Missing push prerequisite: log, no crash | Core path in core-port; build-time `google-services.json` and host-facing wording → public-api / example-and-release |
| SDK-06 | Token refresh re-registers (OS hook) | `FSR#tokenReachesTheLiveCore` (service `onNewToken` → a new `POST /devices` with the new token) |
| SDK-08 | `requestPermission` shows the OS prompt | `PR#grantedAnswerResolvesTrueOnceAndFinishes`, `PR#deniedAnswerResolvesFalse`, `R#defaultPermissionRequesterShowsTheSdkDialogFromTheCurrentActivity` (Robolectric); real device → example-and-release checklist |
| SDK-11 | Below Android 13: auto-grant, no Activity | PR#`` `requestPermission on Android below 13 resolves granted without ever touching the current activity` `` |
| SDK-17 | Foreground push → received | `FSR#messageIsHandedToTheReceivedCallbackParsed`, FS#`` `parseRemoteMessage extracts title, body, and custom data` ``; listener emission → public-api |
| SDK-18 | Tap → clicked (incl. cold start) | AL#`` `a cold-start click detected before any module exists is held for the JS pull, not replayed as an event` ``, `CR#emitWithNoEmitterBuffersOneClickTakenOnce`, `CR#attachedEmitterReceivesClicksAndNothingIsBuffered`; listener and `getInitialNotificationClick` → public-api |
| SDKCTR-01 | Tap reports `clicked` (body tap also `opened`) | AL#`` `a body tap carrying the SDK ids enqueues exactly one opened and one clicked event` `` |
| SDKCTR-02 | Cold-start tap still reports | AL#`` `a cold-start body tap enqueues one opened and one clicked event and still buffers the click for JS` `` |
| SDKCTR-03 | Non-default action excluded | RN 0.8.0 Android has no notification action buttons; N/A until M3 (spec Out of Scope) |
| SDKCTR-04 | Skip without ids | AL#`` `a clicked notification without the SDK ids is not enqueued` ``, FS#`` `onMessageReceived skips enqueue when the SDK ids are absent` ``, FS#`` `onMessageReceived skips enqueue when only one of the SDK ids is present` `` |
| SDKCTR-05 | Foreground receive reports `received` | FS#`` `onMessageReceived enqueues a received event when notification_id and delivery_id are present` ``, `FSR#messageWithNoRuntimeCreatesItAndPersistsTheReceivedEvent` |
| SDKCTR-06 | No background `received` expected | Documented limitation (spec Out of Scope; README draft); no code |
| SDKCTR-13 | Flush on reconnect: observer registered at process start | `IP#createsTheRuntimeAndRegistersTheHooksWithNoNetworkRequest` (default network callback registered) |
| SDKOPEN-01 | Body tap → `opened` | AL#`` `a body tap carrying the SDK ids enqueues exactly one opened and one clicked event` `` |
| SDKOPEN-02 | Cold-start body tap → `opened` | AL#`` `a cold-start body tap enqueues one opened and one clicked event and still buffers the click for JS` `` |
| SDKOPEN-03 | Body tap also reports `clicked` | AL#`` `a body tap carrying the SDK ids enqueues exactly one opened and one clicked event` ``, AL#`` `a body tap whose data carries a URL enqueues the same opened and clicked pair` `` |
| SDKOPEN-04 | At most one pair per tap | AL#`` `the same tap seen by onActivityCreated and onActivityResumed enqueues a single opened and clicked pair` ``, AL#`` `a second warm tap delivering a new intent enqueues a second opened and clicked pair` `` |
| SDKOPEN-05 | Android action buttons | N/A until M3 (no action buttons in RN 0.8.0 Android) |
| SDKOPEN-08 | Skip without ids | AL#`` `a clicked notification without the SDK ids is not enqueued` `` |
| SEGTEL (foreground hook) | `NottiForegroundObserver` on `ProcessLifecycleOwner` at process start | `IP#createsTheRuntimeAndRegistersTheHooksWithNoNetworkRequest` (observer added after the main-looper post) |

---

## Test counts

| Class | Here | RN v0.8.0 | Kind |
| --- | --- | --- | --- |
| `NottiFirebaseMessagingServiceTest` | 6 | 6 | ported (T1) |
| `NottiActivityLifecycleListenerTest` | 20 | 20 | ported (T3) |
| `NottiBridgeTest` | 2 | 2 | ported (T7) |
| `NottiModuleTest` "requestPermission on Android below 13 …" (in `NottiPermissionRequesterTest`) | 1 | 1 | ported (T4) |
| **Ported subtotal** | **29** | **29** | |
| `NottiFirebaseMessagingServiceRuntimeTest` | 5 | - | new (T1) |
| `NottiNotificationClickRelayTest` | 6 | - | new (T2) |
| `NottiCurrentActivityTest` | 7 | - | new (T3) |
| `NottiPermissionRequesterTest` (other cases) | 10 | - | new (T4) |
| `NottiRuntimeTest` (added) | 2 | - | new (T5; class 8 → 10) |
| `NottiInitProviderTest` | 4 | - | new (T6) |
| `NottiBridgeRuntimeTest` | 5 | - | new (T7) |
| `NottiManifestTest` | 4 | - | new (T8) |
| **New subtotal** | **43** | | |
| **push-integration total** | **72** | | |
| **Suite total** (core-port 318 + 72) | **390** | | 0 failures, 0 skipped |

No RN test of a ported class was dropped. RN `NottiModuleTest` cases not ported here: see Deferred.

---

## port-diff summary

`scripts/port-diff.sh <rn-file> <ported-file>` (header, `@file:Suppress`, package/imports, blank lines and whitespace ignored). Numbers are core-port's allowed changes; "PI" = this design's list. No `SPEC_DEVIATION`.

| Ported file | Nature of the remaining hunks |
| --- | --- |
| `NottiFirebaseMessagingService.kt` | 3: `ParsedNotification`, `parseRemoteMessage`, service internal; 2: `toWritableMap` (RN types) removed; PI: `activeCore` → `NottiRuntime.peek()?.core`, `emitNotificationReceived` → `runtime.onNotificationReceived?.invoke(parseRemoteMessage(…))`, `NottiModule.enqueueEvent` → `runtime.enqueueEvent`; design (T7): bodies moved into internal `deliverNewToken` / `deliverRemoteMessage` shared with `NottiBridge` |
| `NottiNotificationClickRelay.kt` | 3: internal (only hunk) |
| `NottiActivityLifecycleListener.kt` | 3: internal ×2; 6: `NottiModule.enqueueEvent` ×2 → `NottiRuntime.get(activity.applicationContext).enqueueEvent`; design tracker lines: `onActivityResumed` → `runtime.activityResumed(activity)` then `handle`, `onActivityDestroyed` → `NottiRuntime.peek()?.activityDestroyed(activity)` |
| `NottiInitProvider.kt` (vs RN lines 1-99, manual) | 3: internal; 5: `Log.e` → `NottiLogger.error` with the class ×2; 6: `getEventStore` → `NottiRuntime.get`; RN In-App block → one-line comment (`in-app-messages`); design: each step in its own `try`; public-api T2 later moved the body unchanged to `NottiProcessHooks.kt` |
| `NottiBridge.kt` | 7: last KDoc sentence (RN `NottiModule` history); PI: bodies → `NottiRuntime.peek()?.let { deliverRemoteMessage(…) }`, `deliverNewToken(…)`; stays public |
| `NottiFirebaseMessagingServiceTest.kt` | 6: statics → `NottiRuntime.resetForTest()` / `setForTest(NottiRuntime(app, fake prefs))`; service built with `Robolectric.setupService` (it now needs its `applicationContext`) |
| `NottiActivityLifecycleListenerTest.kt` | 6: statics as above; 2: RN `ShadowArguments` shadow (RN type) and its imports removed |
| `NottiBridgeTest.kt` | 2: RN fake `ReactApplicationContext` and shadow removed; 6: module `initialize`/`invalidate` → runtime `start`/`stop`, reset before/after; comment naming `ReactApplicationContext` reworded (`check-no-react-native.sh` scans tests) |

New files (formatted by ktlint, no suppress): `NottiPermissionRequester.kt`, `NottiPermissionActivity.kt`, the new test classes, `:example-manual-fcm`.

---

## Suppressions

Per-file `@file:Suppress` on ported code only, for ktlint ids that fire on unchanged RN code (ids from ktlint-cli 1.8.0). No Android Lint suppression; one narrow `@Suppress("DEPRECATION")` on `overridePendingTransition` below API 34 in new code (`NottiPermissionActivity`).

| File | ktlint ids (`ktlint:standard:`) |
| --- | --- |
| `NottiFirebaseMessagingService.kt` | class-signature, function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-call-site |
| `NottiNotificationClickRelay.kt` | function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body |
| `NottiActivityLifecycleListener.kt` | blank-line-before-declaration, chain-method-continuation, function-signature, no-empty-first-line-in-class-body |
| `NottiInitProvider.kt` | function-signature, trailing-comma-on-declaration-site |
| `NottiFirebaseMessagingServiceTest.kt` | function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-declaration-site |
| `NottiActivityLifecycleListenerTest.kt` | chain-method-continuation, function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-call-site, trailing-comma-on-declaration-site |
| `NottiBridgeTest.kt` | multiline-expression-wrapping, no-empty-first-line-in-class-body |
| `NottiBridge.kt` | none |

---

## Design interpretations (not deviations)

| Point | What was done | Why |
| --- | --- | --- |
| Permission Activity recreated with saved state | Does not ask again and waits for the answer; finishes only when the id is no longer pending (answered or process death) or missing | Design step 4 says "finish without asking again" for a request that already ran; finishing while the dialog is still up would drop the answer the system delivers to the new instance, against "survives host recreation" (Tech Decisions) |
| `NottiBridge.onMessageReceived` enqueues `received` | Unlike RN's bridge (emit only), it runs the service path | PUSH-13 ("the result SHALL equal the manifest service's") and design ("Same bodies as the service") |
| T3 → T6 temporary test line | Between T3 and T6 the two listener cases that build `NottiInitProvider` called `registerOnce(app)`; T6 restored RN's line | The provider did not exist before T6; final state is RN-identical |

---

## Deferred

| Item | Owner | Note |
| --- | --- | --- |
| RN `NottiModuleTest` click handover / no-replay cases: `` `invalidate releases the process-wide hooks so a torn-down instance stops receiving clicks` ``, `` `getInitialNotificationClick hands over a cold-start click buffered before the module existed` ``, `` `constructing the module does not replay the buffered cold-start click as an event` ``, `` `getInitialNotificationClick resolves null when the app was not launched from a notification` `` | public-api | They test the module's attach/`takePending` contract; the relay itself is covered here (`CR#`, AL# cold-start cases) |
| RN `NottiModuleTest` In-App cases | in-app-messages / public-api | Module wiring, not push |
| Listener emission (SDK-17/18), `getInitialNotificationClick`, final `NottiBridge` naming | public-api | Internal relays (`NottiRuntime.onNotificationReceived`, `NottiNotificationClickRelay`) are ready |
| Real-device checks: system dialog through `NottiPermissionActivity` (result delivered with the system dialog on top), push received/tapped, provider removal | example-and-release | Robolectric does not model the permission controller |
| README final wording (Android integration) | example-and-release | Draft added here (PUSH-08) |
| Installing the process hooks lazily on `initialize` when the provider was removed | public-api | Done in public-api T2/T6 (`NottiProcessHooks`); README section rewritten in public-api T10 |
