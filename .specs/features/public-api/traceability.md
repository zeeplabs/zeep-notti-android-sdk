# Public API Traceability

**Spec**: `.specs/features/public-api/spec.md`
**Design**: `.specs/features/public-api/design.md`
**Status**: Execute done 2026-10-30 (T1-T11, local branch `feat/public-api`); awaiting review/PR

Test names below are exact (`Class#method`; backtick names as written). `F#` = `NottiFacadeTest`, `U#` = `NottiUserFacadeTest`, `N#` = `NottiNotificationsFacadeTest`, `PF#` = `NottiPermissionFacadeTest`, `L#` = `NottiListenersTest`, `T#` = `NottiPublicTypesTest`, `H#` = `NottiProcessHooksTest`, `R#` = `NottiRuntimeTest`, `AL#` = `NottiActivityLifecycleListenerTest`, `J#` = `NottiJavaInteropTest`, `LP#` = `NottiLogPrivacyTest`, `IP#` = `NottiInitProviderTest`.

---

## API requirements

| ID | Requirement | Task(s) | Evidence |
| --- | --- | --- | --- |
| API-01 | initialize, default baseUrl, no throw | T6 | `F#initializeUsesTheSaasBaseUrlWhenNoneIsGiven`, `F#invalidInputNeitherThrowsNorRegistersAndKeepsTheConfig` (blank ids, invalid URL; previous config kept; no id/key in logs) |
| API-02 | Same config no-op, different config registers again | T1, T6 | `F#sameConfigDoesNotRegisterAgainAndADifferentOneDoes` (trailing slash normalizes to the same config), `R#secondStartKeepsTheCoreAndRegistersOnlyForADifferentConfig`, `R#concurrentStartsLeaveOneCoreAndOneExecutor` |
| API-03 | Production runtime on `notti_prefs` | T6 | `F#theFacadeReadsAndWritesNottiPrefs` (reads `notti_device_id`, `logout` writes `notti_pending_external_user_id_clear`) |
| API-04 | login / logout | T6 | `F#loginAndLogoutReachTheCore`, `F#beforeInitializeLoginIsIgnoredAndLogoutConvergesAtRegistration`, `F#callsWithNoRuntimeAreDroppedWithAWarnNamingOnlyTheMethod` |
| API-05 | deviceId and device-id listener | T1, T6 | `F#deviceIdBeforeInitializeIsThePersistedIdOrNull`, `F#deviceIdListenersAreCalledOnceOnTheMainThreadPerChange` (same id on re-registration calls nobody), `R#deviceIdChangeReachesTheRuntimeSlot`, `R#ensureCoreIsLazyAndDoesNotRegister` |
| API-06 | Tags | T7 | `U#addTagAndAddTagsPatchTheMergedTags`, `U#removeTagAndRemoveTagsPatchTheRemainingTags`, `U#tagsAndSubscriptionBeforeInitializeAreIgnoredByTheCore` |
| API-07 | Email, phone | T7 | `U#setEmailAndClearEmailPatchTheEmailField`, `U#setPhoneAndClearPhonePatchThePhoneField`, `U#emailAndPhoneBeforeInitializeArePersistedAndSentAtRegistration` |
| API-08 | Subscription, location sharing | T7 | `U#setSubscriptionFalseSendsTheUnsubscribeTimestamp`, `U#setLocationSharingEnabledWritesOnTheCallersThread`, `U#locationSharingTurnedOffBeforeInitializeClearsTheCountryAtRegistration`, `U#tagsAndSubscriptionBeforeInitializeAreIgnoredByTheCore`, `U#userCallsWithNoRuntimeAreDroppedWithAWarn` |
| API-09 | requestPermission | T8 | `PF#theAnswerArrivesOnceOnTheMainThread`, `PF#beforeInitializeTheAnswerIsFalseWithNoDialog`, `PF#withNoRuntimeTheAnswerIsFalseAndAWarnIsLogged`, `PF#twoRequestsAreEachAnsweredOnce` |
| API-10 | Listeners, payload, isolation | T4, T5, T8 | `N#everyReceivedListenerGetsThePayloadUntilRemoved`, `N#aReceivedListenerAddedBeforeAnyRuntimeWorksOnceEventsFlow`, `L#everyListenerGetsEveryEventInOrderOnTheMainThread`, `L#removeStopsDeliveryIsIdempotentAndDropsTheListener`, `L#addAndRemoveInsideACallbackApplyFromTheNextEvent`, `L#aThrowingListenerIsLoggedByClassAndTheOthersStillRun`, `T#notificationEqualityIsByValue`, `T#toPublicKeepsTheDataExactly`, `T#subscriptionRemoveRunsOnce` |
| API-11 | Main-thread delivery, relay detach | T5, T8 | `L#everyListenerGetsEveryEventInOrderOnTheMainThread`, `L#hooksRunOnTheZeroToOneAndOneToZeroTransitions`, `L#dispatchWithNoListenersPostsNothing`, `N#withAClickedListenerTheTapIsDeliveredNotKept`, `N#afterTheLastClickedListenerIsRemovedTheTapIsKeptAndTakenOnce`, `F#deviceIdListenersAreCalledOnceOnTheMainThreadPerChange` |
| API-12 | Initial click | T4, T8 | `N#afterTheLastClickedListenerIsRemovedTheTapIsKeptAndTakenOnce`, `N#aTapBeforeAnyClickedListenerIsNotReplayedToALaterOne`, `N#withAClickedListenerTheTapIsDeliveredNotKept` (null after delivery) |
| API-13 | Works without the process hook | T2, T3, T6, T10 | `H#twoInstallsRegisterEachHookOnce`, `H#installAfterTheProviderIsANoOp`, `F#initializeWithoutTheProviderInstallsTheHooksOnce`, `F#initializeAfterTheProviderInstallsNoHooks`, `F#initializeFromABackgroundThreadWorksAndKeepsNoContext`, AL#`` `a late install catches the launch tap on resume once` ``, AL#`` `a late install ignores a Recents reopen on resume` ``, AL#`` `a Recents reopen seen by onActivityCreated then onActivityResumed emits nothing` ``; `IP#` (4) unchanged; README "Removing the process-start hook" (three cases, restore limitation) |
| API-14 | ABI, Java, no internal types | T4, T6-T9 | `notti/api/notti.api` (`Notti`, `Notti$User`, `Notti$Notifications`, the value types, three fun interfaces, `NottiBridge`; no internal type in a signature), `:notti:apiCheck`; `J#everyPublicMethodIsCallableFromJava`; `T#optionsDefaultToNoBaseUrl` |
| API-15 | Log level, no PII at any level | T4, T6, T9 | `F#logLevelMapsToTheLoggerAndDefaultsToError`, `T#logLevelMapsAllSixValuesBothWays`, `T#notificationToStringPrintsDataKeysOnly`, `LP#facadeFlowAtVerboseLogsNoSentinel` |

15/15 mapped.

## RN 0.8.0 public methods (outside In-App)

| RN (`NottiModule` / `index.tsx`) | Android | Test |
| --- | --- | --- |
| `initialize(appId, clientKey, {baseUrl})` | `Notti.initialize(context, appId, clientKey, options)` | API-01, API-02 rows |
| `requestPermission(): Promise<boolean>` | `Notti.Notifications.requestPermission(callback)` | API-09 row; RN `` `requestPermission on Android below 13 …` `` stays in push-integration (PR#) |
| `login` / `logout` | `Notti.login` / `Notti.logout` | API-04 row |
| `User.addTag` / `addTags` / `removeTag` / `removeTags` | `Notti.User.*` (same names) | API-06 row |
| `User.setEmail` / `clearEmail` / `setPhone` / `clearPhone` | `Notti.User.*` (same names) | API-07 row |
| `setSubscription` / `setLocationSharingEnabled` | `Notti.setSubscription` / `Notti.setLocationSharingEnabled` | API-08 row |
| `getDeviceId()` + `deviceIdChanged` event | `Notti.deviceId` + `Notti.addDeviceIdChangedListener` | API-05 row |
| `notificationReceived` / `notificationClicked` events | `Notti.Notifications.addReceivedListener` / `addClickedListener` | API-10, API-11 rows |
| `getInitialNotificationClick()` | `Notti.Notifications.takeInitialClick()` | API-12 row |
| `trackEvent` | not here | `in-app-messages` (as iOS) |

## RN IDs and module tests routed here

| From | Item | Evidence |
| --- | --- | --- |
| core-port | SDK-01 facade, SDK-07 facade rules | API-01, API-02 rows |
| core-port | SDK-12/13 `Notti.User` tag facade | API-06 row |
| core-port / push-integration | SDK-17 listener emission | `N#everyReceivedListenerGetsThePayloadUntilRemoved` |
| core-port / push-integration | SDK-18 listener and `getInitialNotificationClick` | `N#withAClickedListenerTheTapIsDeliveredNotKept`, `N#aTapBeforeAnyClickedListenerIsNotReplayedToALaterOne` |
| core-port | SDKOPEN-10 host events unchanged for a body tap | `N#withAClickedListenerTheTapIsDeliveredNotKept` (payload = the relay's, `data` as is) |
| core-port | SEGTEL-15 JS toggle | `U#setLocationSharingEnabledWritesOnTheCallersThread` |
| core-port | DPF-17 facade | API-07 row |
| core-port / push-integration | SDK-04 host-facing wording | `F#invalidInputNeitherThrowsNorRegistersAndKeepsTheConfig`; `google-services.json` wording stays with `example-and-release` |
| push-integration | RN `NottiModuleTest` `` `invalidate releases the process-wide hooks so a torn-down instance stops receiving clicks` `` | `N#afterTheLastClickedListenerIsRemovedTheTapIsKeptAndTakenOnce` (relay detached on the last remove) |
| push-integration | RN `` `getInitialNotificationClick hands over a cold-start click buffered before the module existed` `` and `` `constructing the module does not replay the buffered cold-start click as an event` `` | `N#aTapBeforeAnyClickedListenerIsNotReplayedToALaterOne` |
| push-integration | RN `` `getInitialNotificationClick resolves null when the app was not launched from a notification` `` | `N#withAClickedListenerTheTapIsDeliveredNotKept`, `J#everyPublicMethodIsCallableFromJava` |
| push-integration | Installing the process hooks on `initialize` when the provider was removed | API-13 row |
| push-integration | Final `NottiBridge` naming | Kept as is (`NottiBridge.onMessageReceived` / `onNewToken`) |

## Test counts

444 unit tests (was 390 before this feature): +54.

| Class | Tests | Change |
| --- | --- | --- |
| `NottiRuntimeTest` | 13 | +3 new; `replacingCoreShutsTheOldExecutorDown` rewritten as `secondStartKeepsTheCoreAndRegistersOnlyForADifferentConfig`, `stopIsIdentityGuarded` adapted (T1) |
| `NottiProcessHooksTest` | 2 | new (T2) |
| `NottiActivityLifecycleListenerTest` | 23 | +3 new; the 20 ported cases unchanged (T3) |
| `NottiPublicTypesTest` | 6 | new (T4) |
| `NottiListenersTest` | 6 | new (T5) |
| `NottiFacadeTest` | 13 | new (T6) |
| `NottiUserFacadeTest` | 10 | new (T7) |
| `NottiNotificationsFacadeTest` | 5 | new (T8) |
| `NottiPermissionFacadeTest` | 4 | new (T8) |
| `NottiJavaInteropTest` (Java) | 1 | new (T9) |
| `NottiLogPrivacyTest` | 3 | +1 (T9) |

`NottiCoreGapsTest` and `NottiContinuityTest` pass the permission requester and the device-id slot the new way (T1); counts unchanged.

## Changes to ported or earlier code

| File | Change | Task |
| --- | --- | --- |
| `NottiRuntime.kt` (new in core-port) | `ensureCore()`; `start` = `ensureCore` + `initialize` (no core replacement, AD-014); `permissionRequester` and `apiClientFactory` constructor parameters; `onDeviceIdChanged` slot | T1, T6 |
| `NottiInitProvider.kt` (ported) | `onCreate` body moved unchanged to `NottiProcessHooks.installOnce` | T2 |
| `NottiActivityLifecycleListener.kt` (ported) | `onActivityResumed` skips `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` (`port-diff`: only this hunk beyond push-integration's) | T3 |

## Design interpretations (not deviations)

| Item | Design | Implemented | Why |
| --- | --- | --- | --- |
| Hooks guard | `AtomicBoolean` | Once per `Application` instance (weak reference) | Every hook registers on that Application; one per process in an app, and a test Application gets its own hooks |
| Received / device-id slots | Set on the first listener, cleared on the last | Set whenever the facade reaches a runtime (`initialize`, any call, adding a listener) and never cleared | A listener added before any runtime exists would otherwise never be wired; dispatching to no listener is a no-op |
| Before-initialize rows of T6/T7 | "follow the core's pending queue" | The core's rule as ported: `login`, tags and `setSubscription` are ignored with an error line before `initialize`; `logout`, email, phone and location sharing persist and converge | RN 0.8.0 behavior (AD-002); README states it |

## Deferred

| Item | Owner | Note |
| --- | --- | --- |
| Restore half of the inherited phantom click (provider present: restored Activity reported again on resume) | STATE todo | Recents half fixed in T3 |
| `trackEvent` | in-app-messages | As iOS |
| Final README wording, device checklist, `google-services.json` guidance | example-and-release | README sections marked draft |
