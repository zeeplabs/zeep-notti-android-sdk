# Core Port Traceability

**Spec**: `.specs/features/core-port/spec.md`
**Design**: `.specs/features/core-port/design.md`
**Status**: Execute done 2026-10-28 (T1-T14, local branch `feat/core-port`); awaiting review/PR

Test names below are exact (`Class#method`; backtick names as written). `C#` = `NottiCoreTest`, `A#` = `NottiApiClientTest`, `DS#` = `NottiDeviceStoreTest`, `ES#` = `NottiEventStoreTest`, `P#` = `NottiAndroidProvidersTest`, `R#` = `NottiRuntimeTest`, `G#` = `NottiCoreGapsTest`, `FO#` = `NottiForegroundObserverTest`, `NO#` = `NottiNetworkObserverTest`. "→ feature" = owned by that feature, with the reason.

---

## CORE requirements

| ID | Requirement | Task(s) | Evidence |
| --- | --- | --- | --- |
| CORE-01 | Core ported, internal, no RN symbols | T2-T6, T9, T13 | Ported files under `com.zeeplabs.notti`, all `internal` (`notti/api/notti.api` empty, `apiCheck`); `scripts/check-no-react-native.sh` |
| CORE-02 | All dependencies injectable | T6, T7, T8 | `NottiCore` constructor unchanged from RN; `NottiAndroidProviders` seams; `NottiRuntime` internal constructor |
| CORE-03 | Container replaces companion statics | T8, T9 | `R#getIsLazySingleInstanceAcrossThreads`, `R#stopIsIdentityGuarded`, `R#replacingCoreShutsTheOldExecutorDown`, `R#observersReachTheRuntimeThroughPeek`, `FO#` (3) |
| CORE-04 | Pre-core events persisted and flushed | T8 | `R#enqueueWithoutCorePersists`, `R#eventEnqueuedBeforeAnyCoreIsFlushedAfterStartAndRegistration` |
| CORE-05 | Native `sdk_version` | T8 | `R#registrationSyncSendsTheSdkVersionConstant` (profile-field PATCH after registration, as RN) |
| CORE-06 | Providers keep RN 0.8.0 behavior | T7 | `P#` success cases (27 tests) |
| CORE-07 | Provider failure returns null | T7 | `P#appVersionMissingPackageIsNull`, `P#permissionStatusReadFailureIsNull`, `P#countryWithoutLastLocationIsNull`, `P#countryGeocoderFailureIsNull`, `P#countryWithShutExecutorIsNull`, `P#fcmTokenFailureIsNull`, `P#fcmTokenThrowingSeamIsNull`, `P#fcmTokenWithoutFirebaseAppIsNullNoCrash` |
| CORE-08 | Providers unit-tested | T7 | `NottiAndroidProvidersTest` (27) |
| CORE-09 | Suite ported, counts, drop list | T2-T6, T9 | "Test counts" below: 257 ported = RN 257, no RN test dropped; one test adapted (deferred list) |
| CORE-10 | Every applicable RN ID mapped | T14 | "RN requirement mapping" below |
| CORE-11 | Direct tests for SDK-04/08/12/13/17 | T12 | `NottiCoreGapsTest` (5); SDK-17 → push-integration/public-api |
| CORE-12 | Same file, keys, types, encodings | T2, T10 | "Storage contract" below; `NottiContinuityTest#registeredFixtureReadsBackWithRnTypes` |
| CORE-13 | No re-register on migrated state (unless RN would) | T10 | `NottiContinuityTest#registeredFixtureKeepsDeviceIdAndDoesNotRegisterAsNew`, `#tokenChangedFixtureReRegistersWithTheNewTokenAndKeepsTheId`, `#fixtureAnsweredWithANewServerRowAdoptsItAndResetsAcks` |
| CORE-14 | Fixture and golden file-name tests | T8, T10 | `R#productionPrefsFileIsNottiPrefs`, `NottiContinuityTest` (4, on `notti_prefs`) |
| CORE-15 | Single logger, no PII | T1, T6, T7, T9, T11 | `NottiLogger`; rule 5 applied in every ported/moved log line; `NottiLogPrivacyTest` (2); `P#readFailuresLogTheExceptionClassOnly` |
| CORE-16 | Errors-only default | T1 | `NottiLoggerTest#defaultLevelIsError`, `#errorLevelEmitsErrorOnly`, `#noneEmitsNothing`, `#lazyMessageNotEvaluatedBelowLevel`; `NottiLoggerSinkTest` (3) |
| CORE-17 | Sentinel test | T11 | `NottiLogPrivacyTest#fullFlowWithFailuresLogsNoSentinel`, `#tokenFetchFailureLogsTheClassNotTheMessage` (leak check done once and reverted, T11 commit) |

---

## RN requirement mapping

### sdk-core-v1 (SDK-01..19)

| ID | Behavior | Tests here / owner |
| --- | --- | --- |
| SDK-01 | initialize → FCM token → `POST /devices {token, platform}`, Bearer client key | C#`` `initialize happy path registers the device and persists id and tags` ``, A#`` `createOrUpdateDevice sends POST with correct headers and body` ``, `P#fcmTokenSuccess`; facade → public-api |
| SDK-02 | Persist device id, cache tags | C#`` `initialize happy path registers the device and persists id and tags` ``, C#`` `initialize happy path exposes the persisted id via getDeviceId` ``, C#`` `first registration notifies onDeviceIdChanged with the newly assigned id` `` |
| SDK-03 | Missing appId/clientKey/baseUrl: log, no crash, no registration | C#`` `initialize with blank appId logs and does not call the API client` ``, C#`` `initialize with blank clientKey logs and does not call the API client` ``, C#`` `initialize with blank baseUrl logs and does not call the API client` ``, C#`` `initialize with a malformed baseUrl logs, registers nothing, and leaves the SDK non-crashing` `` |
| SDK-04 | Missing native push prerequisite: log, no crash | `G#sdk04MissingFirebaseAppNeitherCrashesNorRegistersAndLogsWhy`, C#`` `initialize with no available push token logs and does not register` ``, C#`` `initialize with an async token fetch that fails does not crash and does not attempt registration` ``, `P#fcmTokenWithoutFirebaseAppIsNullNoCrash`; build-time `google-services.json` and host-facing wording → push-integration / public-api |
| SDK-05 | Registration retry 5x backoff, then wait for foreground/token refresh | A#`` `5xx responses exhaust the retry cap at exactly 5 attempts` ``, A#`` `network error is retried per the same backoff schedule` ``, A#`` `5xx response retries then succeeds without exhausting the cap` ``, C#`` `registration that exhausted its retry cap is resumed on the next app foreground` ``, C#`` `repeated foregrounds while an attempt is in flight do not stack registration attempts` ``, C#`` `a token fetch that never completes does not disable the next foreground retry` `` |
| SDK-06 | Token refresh re-registers | C#`` `onTokenRefreshed re-registers with the new token` ``; OS hook (`FirebaseMessagingService.onNewToken`) → push-integration |
| SDK-07 | Repeat initialize is a no-op | C#`` `repeat initialize with identical args is a no-op` ``; facade rules (AD-008) → public-api |
| SDK-08 | requestPermission triggers the OS prompt | `G#sdk08RequestPermissionCallsTheInjectedRequesterAndPatchesItsAnswer`, `G#sdk08DefaultRuntimeRequesterDeniesWithoutPromptingUntilPushIntegration`, C#`` `requestPermission called before initialize logs and does not prompt` ``; real OS dialog → push-integration |
| SDK-09 | Grant → PATCH `subscribed: true` | C#`` `requestPermission grant path invokes the native prompt and PATCHes subscribed true` `` |
| SDK-10 | Deny → PATCH `subscribed: false` | C#`` `requestPermission deny path PATCHes subscribed false` `` |
| SDK-11 | Android < 13 needs no runtime permission (auto-grant, no Activity) | → push-integration (`requestNativePermission` is the injected `permissionRequester`; RN tested it on `NottiModule`); the status read below 33 is `P#permissionStatusApi32Enabled`, `P#permissionStatusApi32Disabled` |
| SDK-12 | addTags merges, PATCHes the full map | `G#sdk12AddTagsMergesIntoTheCachedMapAndPatchesTheFullMap`, C#`` `two rapid tag mutations serialize and converge to the correct net merged result` ``, DS#`` `mergeTags adds, removes, and last-operation-wins on overlapping add plus remove` ``; facade → public-api |
| SDK-13 | removeTags drops keys, PATCHes the full map | `G#sdk13RemoveTagsDropsTheKeyAndPatchesTheFullRemainingMap`, DS#`` `setTags with a key removed from a prior call is no longer present` ``; facade → public-api |
| SDK-14 | login → PATCH `external_user_id` | C#`` `login PATCHes external_user_id and the cached token, and persists it locally on success` `` |
| SDK-15 | logout clears the local id (superseded by DLOG: also server-side) | C#`` `logout clears the external user id server-side and clears email and phone` `` |
| SDK-16 | setSubscription → PATCH `subscribed` | C#`` `setSubscription PATCHes the given subscribed value and the cached token, and persists it locally on success` `` |
| SDK-17 | Foreground push → `notificationReceived` event | → public-api (listener emission) and push-integration (`onMessageReceived`, `parseRemoteMessage`); no core part |
| SDK-18 | Tap → `notificationClicked` event (incl. cold start) | → push-integration (click relay, Activity hook) and public-api (listener) |
| SDK-19 | Mutations serialized, last write wins | C#`` `mutation calls never perform their PATCH on the calling thread` ``, C#`` `two rapid tag mutations serialize and converge to the correct net merged result` ``, C#`` `mutations queued before registration are flushed in call order and merged against the registered tags` ``, C#`` `a concurrent registration cannot clobber the tag map an in-flight addTags just merged` `` |

### ctr-event-reporting (SDKCTR-01..14)

| ID | Behavior | Tests here / owner |
| --- | --- | --- |
| SDKCTR-01 | Tap reports `clicked` (body tap also `opened`) | Report path: C#`` `registration success flushes a queued event and removes it` ``, A#`` `reportEvent sends POST to the events endpoint with bearer auth and snake_case body` ``; tap detection → push-integration |
| SDKCTR-02 | Cold-start tap still reports | `R#enqueueWithoutCorePersists`, `R#eventEnqueuedBeforeAnyCoreIsFlushedAfterStartAndRegistration`; detection → push-integration |
| SDKCTR-03 | Non-default action excluded (superseded by SDKOPEN-05/06) | → push-integration (Android action handling); iOS-only parts not applicable |
| SDKCTR-04 | Skip without `notification_id`/`delivery_id` | → push-integration (parse at the detection site) |
| SDKCTR-05 | Foreground receive reports `received` | Report path: C#`` `registration success flushes a queued event and removes it` `` (type `received`); `onMessageReceived` → push-integration |
| SDKCTR-06 | No background `received` expected | → push-integration (documented limitation; no core behavior) |
| SDKCTR-07 | Network/5xx retried 5x | A#`` `reportEvent retries 5xx responses and exhausts the retry cap at 5 attempts` ``, A#`` `reportEvent 5xx exhaustion is a non-terminal failure` `` |
| SDKCTR-08 | 4xx terminal, 408/429 transient | A#`` `reportEvent does not retry 4xx responses` ``, A#`` `reportEvent treats 429 as transient - full retry schedule, then a non-terminal failure` ``, A#`` `reportEvent treats 408 as transient - retried with backoff, then a non-terminal failure` ``, A#`` `reportEvent retries a 429 and succeeds once the backend recovers` ``, C#`` `flushEventQueue drops the event on a terminal 4xx report` `` |
| SDKCTR-09 | Exhausted retries → stays queued | C#`` `flushEventQueue keeps the event when reportEvent fails` ``, C#`` `flush stops at the first transient failure instead of burning retries on every event` `` |
| SDKCTR-10 | Write-ahead persistence | ES#`` `enqueue then all returns the stored record` ``, ES#`` `a fresh store over the same SharedPreferences sees previously enqueued events` ``, ES#`` `concurrent enqueue and remove never lose an event` ``, `R#enqueueWithoutCorePersists` |
| SDKCTR-11 | Removed on success or terminal | C#`` `registration success flushes a queued event and removes it` ``, C#`` `flushEventQueue drops the event on a terminal 4xx report` ``, ES#`` `remove then all no longer contains the removed record` `` |
| SDKCTR-12 | Flush on launch | C#`` `registration success flushes a queued event and removes it` ``, C#`` `onAppForegrounded flushes the event queue even when already REGISTERED` ``, `R#eventEnqueuedBeforeAnyCoreIsFlushedAfterStartAndRegistration` |
| SDKCTR-13 | Flush on reconnect | NO#`` `register captures a default-network callback and an available network triggers the flush` ``, NO#`` `onLost and onUnavailable do not trigger the flush` ``, C#`` `repeated flush triggers while one is pending are coalesced into a single flush` ``; registering the observer → push-integration |
| SDKCTR-14 | Duplicate report after crash tolerated (no idempotency key) | **GAP** (see GAPS): by design, no test in RN either |

### opened-event-reporting (SDKOPEN-01..11)

| ID | Behavior | Tests here / owner |
| --- | --- | --- |
| SDKOPEN-01 | Body tap → `opened` | Report path: C#`` `a queued opened event is flushed with type opened and removed on 2xx` ``; detection → push-integration |
| SDKOPEN-02 | Cold-start body tap → `opened` | `R#eventEnqueuedBeforeAnyCoreIsFlushedAfterStartAndRegistration` (pre-core queue); detection → push-integration |
| SDKOPEN-03 | Body tap also reports `clicked` | → push-integration (two enqueues per tap at the detection site) |
| SDKOPEN-04 | At most one `opened`/`clicked` per tap | → push-integration (dedup at the click hook) |
| SDKOPEN-05 | iOS custom action → `clicked` only | iOS-only; Android action buttons → push-integration |
| SDKOPEN-06 | iOS dismiss → no event | iOS-only |
| SDKOPEN-07 | iOS custom action → no JS event | iOS-only |
| SDKOPEN-08 | Skip without ids / non-remote push | → push-integration (parse) |
| SDKOPEN-09 | `opened` uses the same queue/retry/flush, body `type: "opened"` | C#`` `a queued opened event is flushed with type opened and removed on 2xx` ``, A#`` `reportEvent sends POST to the events endpoint with bearer auth and snake_case body` `` |
| SDKOPEN-10 | Host events unchanged for body tap | → public-api |
| SDKOPEN-11 | README + CHANGELOG | → example-and-release (docs) |

### segment-telemetry-reporting (SEGTEL-01..15)

| ID | Behavior | Tests here / owner |
| --- | --- | --- |
| SEGTEL-01 | Read app version | `P#appVersionReadsVersionName`, `P#appVersionMissingPackageIsNull` |
| SEGTEL-02 | App version in the registration/flush payload | C#`` `registration success with a version provider diff enqueues a PATCH with the current app version` `` |
| SEGTEL-03 | Changed version → diff and enqueue | C#`` `a bumped app version between two registrations enqueues a PATCH with the new value` ``, C#`` `registration success with an unchanged app version does not enqueue a PATCH` ``, C#`` `registration success with a null version provider enqueues nothing and does not crash` `` |
| SEGTEL-04 | Version sent as an opaque string | C#`` `registration success with a version provider diff enqueues a PATCH with the current app version` `` (value sent as read) |
| SEGTEL-05 | Foreground starts a session, sets `first_session_at` | C#`` `first_session_at is set on the first session start and never overwritten later` ``, C#`` `a second foreground signal for the same process session does not open a second session` ``, C#`` `foreground sync on core creation starts the cold-start session when the process is already started` ``, C#`` `foreground sync on core creation does nothing when the process is not started` `` |
| SEGTEL-06 | Background ends the session (count, time, last) | C#`` `session end after a foreground start aggregates count and time and enqueues a snapshot PATCH` ``, C#`` `session end with no active session is a no-op with no PATCH` ``, FO#`` `heartbeat ticks every interval while started and stops after onStop` ``, FO#`` `onStop with no live core still closes the process session gate` `` |
| SEGTEL-07 | Snapshot PATCH per session end; telemetry coalesced before registration | C#`` `session end after a foreground start aggregates count and time and enqueues a snapshot PATCH` ``, C#`` `telemetry never evicts queued subscription or tag mutations and is coalesced per key` `` |
| SEGTEL-08 | Unclean kill closed at the last heartbeat | C#`` `a session start after an unclean kill closes the missed session at the last heartbeat, not at relaunch time` ``, C#`` `an orphaned session with no heartbeat contributes zero time instead of the dead interval` ``, C#`` `an orphaned session heartbeat is bounded by the max orphan session cap` ``, FO#`` `heartbeat ticks every interval while started and stops after onStop` `` |
| SEGTEL-09 | Session state persisted locally | DS#`` `telemetry fields round-trip through SharedPreferences` ``, DS#`` `a fresh store over the same SharedPreferences sees persisted telemetry values` ``, C#`` `RN reload - background with no live core closes the process gate so the next foreground opens a session` `` |
| SEGTEL-10 | Location off by default | C#`` `opt-in off with permission granted never reads or sends a country field` ``, DS#`` `telemetry fields default to null-zero-false when absent` `` |
| SEGTEL-11 | Opt-in + permission → country at session start | C#`` `opt-in on with permission granted enqueues a country PATCH at session start` ``, C#`` `an unchanged country is not re-sent on every foreground` ``, `P#countryResolvesCountryCode` |
| SEGTEL-12 | No opt-in or no permission → field omitted | C#`` `opt-in on with no OS location permission never invokes the provider` ``, C#`` `a country read queued before registration is never sent if the user opts out first` ``, `P#locationNoneIsFalse`, `P#locationCoarseOnlyIsEnough`, `P#locationFineOnlyIsEnough` |
| SEGTEL-13 | Opt-out clears the synced country (persisted until 2xx) | C#`` `toggling location sharing off enqueues an explicit country null clear` ``, C#`` `opting out before initialize still clears country once registration completes` ``, C#`` `a failed country clear stays pending and is re-sent on the next foreground` ``, C#`` `opting out without ever having opted in sends no clear PATCH` ``, C#`` `a pending country clear runs before queued mutations on registration and is sent exactly once` ``, C#`` `a country clear rejected with a permanent 4xx logs distinctly and stays pending` ``, C#`` `a transient clear failure keeps the generic log, not the permanent-4xx one` ``, C#`` `repeated country-clear retry triggers while one is pending are coalesced into a single attempt` ``, C#`` `re-consent after a clear whose ack was lost forgets the synced country so it is re-sent` ``, C#`` `re-enabling while already enabled does not drop the synced country` ``, DS#`` `opt-out flags are written with a synchronous commit so they survive process death` `` |
| SEGTEL-14 | Never prompts; read failure = omit | C#`` `opt-in on with a failing country provider omits the field without crashing` ``, `P#countryWithoutLastLocationIsNull`, `P#countryGeocoderFailureIsNull`, `P#countryWithShutExecutorIsNull` |
| SEGTEL-15 | Opt-in persists across restarts | DS#`` `telemetry fields round-trip through SharedPreferences` ``, DS#`` `opt-out flags are written with a synchronous commit so they survive process death` ``; JS toggle → public-api |

### device-profile-fields (DPF-01..21)

| ID | Behavior | Tests here / owner |
| --- | --- | --- |
| DPF-01 | Capture OS, model, SDK version | `P#deviceOsAndModelReadBuild`, `R#registrationSyncSendsTheSdkVersionConstant` |
| DPF-02 | Fields in the registration sync | C#`` `registration success with profile providers enqueues a PATCH with all six fields` ``, C#`` `sdk_version passed through initialize reaches the payload` `` |
| DPF-03 | Diff and enqueue on change (echo ack) | C#`` `only a changed profile field is re-sent between two registrations` ``, C#`` `a PATCH ack that does not echo the field leaves it unsynced and re-sends it on the next registration` ``, C#`` `a PATCH ack echoing a different value does not mark the field synced` ``, C#`` `a registration that answers with a new device id re-sends every profile field` ``, C#`` `an install upgraded from 0_5_0 re-sends profile fields it had marked synced without an ack` ``, A#`` `patchDevice ack exposes the echoed profile fields` ``, DS#`` `profile field ack migration clears the six last-synced values exactly once` ``, DS#`` `clearProfileFieldAcks forgets every profile ack and the permission status` `` |
| DPF-04 | Read failure non-fatal | C#`` `a null profile provider omits that field without crashing` ``, `P#appVersionMissingPackageIsNull` |
| DPF-05 | `app_version` unchanged | C#`` `registration success with a version provider diff enqueues a PATCH with the current app version` ``, C#`` `registration success with an unchanged app version does not enqueue a PATCH` `` |
| DPF-06 | Capture timezone and language | `P#timezoneIdReadsDefaultZone`, `P#languageIsPrimarySubtag`, C#`` `language normalization keeps the primary subtag and maps legacy codes` `` |
| DPF-07 | Timezone/language in the registration sync | C#`` `registration success with profile providers enqueues a PATCH with all six fields` `` |
| DPF-08 | Timezone/language diff and enqueue | C#`` `only a changed profile field is re-sent between two registrations` `` |
| DPF-09 | Timezone/language read failure non-fatal | C#`` `a null profile provider omits that field without crashing` ``, `P#languageUndeterminedIsNull` |
| DPF-10 | Read permission status | C#`` `permission status mapping covers every branch` ``, `P#permissionStatusApi32Enabled`, `P#permissionStatusApi32Disabled`, `P#permissionStatusApi34GrantedAndEnabled`, `P#permissionStatusApi34GrantedButDisabled`, `P#permissionStatusApi34NotGrantedNoActivityIsNull`, `P#permissionStatusApi34NotGrantedRationaleTrueIsDenied`, `P#permissionStatusApi34NotGrantedRationaleFalseIsNotDetermined`, `P#permissionStatusApi34RequestedBeforeIsDenied`, `P#permissionStatusReadFailureIsNull` |
| DPF-11 | Permission status in the registration sync | C#`` `registration success syncs permission status` `` |
| DPF-12 | requestPermission result synced | C#`` `requestPermission result syncs the OS permission status` ``, C#`` `a re-subscribe through requestPermission clears a stale pending unsubscribe stamp` ``, C#`` `a granted requestPermission whose PATCH fails still clears the pending unsubscribe stamp` `` |
| DPF-13 | Settings change caught at session start | C#`` `a granted to denied Settings change is caught at the next session start` ``, C#`` `a null permission read neither diffs nor drops a pending granted to denied stamp` `` |
| DPF-14 | `last_unsubscribed_at` on true→false / granted→denied | C#`` `setSubscription false on a subscribed device sets last_unsubscribed_at while permission stays granted` ``, C#`` `setSubscription false with unknown local state records last_unsubscribed_at` ``, C#`` `setSubscription false on an already unsubscribed device does not record last_unsubscribed_at` ``, C#`` `a retried setSubscription false re-sends the timestamp of the first detection` ``, C#`` `a retried granted to denied permission sync re-sends the timestamp of the first detection` ``, C#`` `two denied reads before registration stamp the first detection time` ``, C#`` `denied then granted then denied before registration stamps the third read` ``, DS#`` `lastUnsubscribedAtMs uses the -1L sentinel so an absent value reads as null` ``, DS#`` `subscribed tri-state reads null until a value is persisted` ``, DS#`` `pending unsubscribe timestamps default to null and round-trip` `` |
| DPF-15 | Not cleared on re-subscribe | C#`` `re-subscribe does not clear last_unsubscribed_at` `` |
| DPF-16 | `subscribed` and `permission_status` independent | C#`` `setSubscription false on a subscribed device sets last_unsubscribed_at while permission stays granted` ``, C#`` `an unknown permission status is omitted without crashing` `` |
| DPF-17 | Email/phone set → enqueue | C#`` `setEmail enqueues a PATCH with email and never touches tags` ``, C#`` `setPhone enqueues a PATCH with phone` ``, C#`` `setEmail after logout and a new login converges to the new email` ``, C#`` `back-to-back sets converge lastSynced to the newest value` ``; facade → public-api |
| DPF-18 | Explicit clear | C#`` `clearEmail enqueues an explicit email null clear` ``, C#`` `clearEmail before initialize with a previously synced email is sent as email null at registration` ``, C#`` `a clear whose PATCH failed is re-sent at the next registration` ``, C#`` `a set acked after a newer clear still records lastSynced so a failed clear is re-sent at registration` ``, C#`` `logout before registration supersedes a queued setEmail with the clear` `` |
| DPF-19 | Email/phone in the registration sync | C#`` `registration success re-sends a held email and phone` ``, C#`` `registration success with no held email or phone sends nothing for them` ``, C#`` `registration resync records the acknowledged held value as synced` ``, DS#`` `lastSynced email and phone round-trip and are written with a synchronous commit` `` |
| DPF-20 | No-op on unchanged value | C#`` `setEmail twice with the same value enqueues a single mutation` ``, C#`` `setEmail with the same value after a failed PATCH re-enqueues it` `` |
| DPF-21 | Never merged into tags | C#`` `setEmail enqueues a PATCH with email and never touches tags` `` |

### logout-clears-external-id (DLOG) and RN #41

The RN spec defines DLOG-10..15 and DLOG-22 (no DLOG-01..09 or 16..21 exist).

| ID | Behavior | Tests here / owner |
| --- | --- | --- |
| DLOG-10 | logout sends `external_user_id: null` through the queue | C#`` `logout clears the external user id server-side and clears email and phone` `` |
| DLOG-11 | login then logout ends null | C#`` `logout called right after login leaves the device logged out` ``, C#`` `login queued before registration then logout still ends with the id cleared` `` |
| DLOG-12 | logout then login ends with the new id | C#`` `logout then login ends linked to the new user` `` |
| DLOG-13 | Clear re-sent after failure / before init | C#`` `a failed logout clear is re-sent at the next registration` ``, C#`` `logout before initialize sends the clear once registration completes` ``, `NottiContinuityTest#registeredFixtureKeepsDeviceIdAndDoesNotRegisterAsNew` (RN pending flag honored) |
| DLOG-14 | Tags and subscription untouched | Asserted inside C#`` `logout clears the external user id server-side and clears email and phone` `` (as RN) |
| DLOG-15 | Harmless against an old server | **GAP** (see GAPS): server-side behavior, no client test in RN either |
| DLOG-22 | CHANGELOG + README | → example-and-release (docs) |
| RN #41 | Only the latest login/logout queued before registration is kept | C#`` `logout login logout queued before registration ends with the id cleared` ``, C#`` `logout then login queued before registration ends linked to the new user` ``, C#`` `login queued before registration then logout still ends with the id cleared` `` |

### In-App data (ported for `NottiApiClient`, amendment 2026-10-28)

`NottiInAppModelsTest` (36) and `NottiInAppEventStoreTest` (13) and the 13 In-App cases of `NottiApiClientTest` cover the data layer only; their `SDKIAM-*` IDs are mapped by `in-app-messages`.

---

## GAPS

RN IDs with no test here and no owning feature:

| ID | Why | Proposal |
| --- | --- | --- |
| SDKCTR-14 | "A duplicate report after a crash between 2xx and local cleanup is allowed" is the absence of an idempotency key; RN mapped it to "design+flush" with no test | Accept as by-design (no client behavior to assert), or add a core test that a re-queued event is re-sent; needs a decision |
| DLOG-15 | Old servers answer the null clear as a 2xx no-op: server-side behavior, RN "Done (no test)" | Accept: not testable in the SDK |

---

## Test counts

| Class | Here | RN v0.8.0 | Kind |
| --- | --- | --- | --- |
| `NottiCoreTest` | 128 | 128 | ported |
| `NottiApiClientTest` | 48 | 48 | ported |
| `NottiDeviceStoreTest` | 20 | 20 | ported |
| `NottiEventStoreTest` | 7 | 7 | ported |
| `NottiInAppModelsTest` | 36 | 36 | ported |
| `NottiInAppEventStoreTest` | 13 | 13 | ported |
| `NottiForegroundObserverTest` | 3 | 3 | ported (one case adapted, see Deferred) |
| `NottiNetworkObserverTest` | 2 | 2 | ported |
| **Ported subtotal** | **257** | **257** | |
| `NottiAndroidProvidersTest` | 27 | - | new (T7) |
| `NottiRuntimeTest` | 8 | - | new (T8) |
| `NottiContinuityTest` | 4 | - | new (T10) |
| `NottiLogPrivacyTest` | 2 | - | new (T11) |
| `NottiCoreGapsTest` | 5 | - | new (T12) |
| `NottiLoggerTest` | 9 | - | new (T1) |
| `NottiLoggerSinkTest` | 3 | - | new (T1) |
| **New subtotal** | **58** | | |
| `NottiSdkVersionTest`, `OrgJsonSmokeTest`, `RobolectricSmokeTest` | 3 | - | package-foundation |
| **Total** | **318** | | |

No RN test of a ported class was dropped.

---

## Deferred

| Item | Owner | Note |
| --- | --- | --- |
| `NottiForegroundObserverTest` `onStop with no live core still ends the In-App session`: coordinator-backed version (`session.isOpen`) | in-app-messages | Here it uses a recording `NottiInAppLifecycle` and asserts `onSessionEnded` + `onAppBackgrounded` |
| SDK-04 build-time `google-services.json` resolution and host-facing error wording | push-integration / public-api | Core runtime path covered by `G#sdk04…` |
| SDK-08 real OS dialog; SDK-11 below-33 auto-grant (`permissionRequester`) | push-integration | Runtime default requester answers `false` without prompting |
| SDK-12/13 `Notti.User` tag facade | public-api | Core merge covered |
| SDK-17/18 listener emission, FCM receive, click relay, `parseRemoteMessage` | public-api / push-integration | No core part |
| `currentActivity` injection (permission rationale on API 33+) | push-integration | See Accepted exceptions |
| Registering `NottiForegroundObserver` / `NottiNetworkObserver` at process start | push-integration | Process hook |

---

## Accepted exceptions

| Exception | Reason | Decided |
| --- | --- | --- |
| AD-011: `NottiCore.initialize` logs an invalid `baseUrl` verbatim (`"baseUrl \"$baseUrl\" is not a valid http(s) URL"`), kept as RN | `baseUrl` is integrator configuration, not user data, and is logged only on a failed `initialize`; the sentinel test runs with a valid URL and stays green | Coordinator, 2026-10-28 |
| `permission_status` omitted on API 33+ for a never-requested, ungranted permission | Until `push-integration` injects `currentActivity`, the rationale read is `null`, which RN maps to "omit" (RN does the same with no resumed Activity); full parity returns with the Activity tracker | Design ("RN coupling map", parity note) |

---

## Suppressions

Every suppression covers RN code unchanged in logic; none relaxes `build.gradle.kts`. ktlint ids are `ktlint:standard:<id>` in a per-file `@file:Suppress`; Android Lint ids as listed.

| File | Android Lint | ktlint | Reason |
| --- | --- | --- | --- |
| `NottiDeviceStore.kt` | ApplySharedPref, UseKtx | chain-method-continuation, class-signature, function-expression-body, function-signature, multiline-expression-wrapping, no-consecutive-blank-lines, no-empty-first-line-in-class-body, trailing-comma-on-call-site, trailing-comma-on-declaration-site, value-parameter-comment | RN style; `commit()` is deliberate (LGPD durability) |
| `NottiEventStore.kt` | UseKtx | class-signature, function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-call-site, trailing-comma-on-declaration-site | RN style |
| `NottiInAppModels.kt` | - | blank-line-between-when-conditions, class-signature, function-signature, multiline-expression-wrapping, trailing-comma-on-call-site, trailing-comma-on-declaration-site, when-entry-bracing | RN style |
| `NottiInAppEventStore.kt` | UseKtx | function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-call-site, trailing-comma-on-declaration-site | RN style |
| `NottiApiClient.kt` | - | blank-line-before-declaration, blank-line-between-when-conditions, chain-method-continuation, class-signature, function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, spacing-between-declarations-with-comments, trailing-comma-on-declaration-site, when-entry-bracing | RN style |
| `NottiCore.kt` | - | argument-list-wrapping, blank-line-before-declaration, blank-line-between-when-conditions, function-signature, max-line-length, multiline-expression-wrapping, no-blank-line-before-rbrace, no-empty-first-line-in-class-body, spacing-between-declarations-with-annotations, trailing-comma-on-call-site, trailing-comma-on-declaration-site, when-entry-bracing, wrapping | RN style; max-line-length: 4 RN lines past 140 columns after the 2→4 re-indent |
| `NottiForegroundObserver.kt` | - | chain-method-continuation, function-signature, multiline-expression-wrapping, trailing-comma-on-declaration-site | RN style |
| `NottiNetworkObserver.kt` | MissingPermission | multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-declaration-site | RN never declared `ACCESS_NETWORK_STATE`; `firebase-messaging` (an `api` dependency) declares it and `register()` catches the exception |
| `NottiAndroidProviders.kt` (new file, moved bodies) | `@SuppressLint("MissingPermission")` on `country()`, `@SuppressLint("InlinedApi")` on `permissionStatus()` | none (formatted) | `country()` runs only after `hasLocationPermission` and catches `SecurityException`; `POST_NOTIFICATIONS` is read only in the API 33+ branch, which lint cannot see through the `sdkInt` local |
| `NottiDeviceStoreTest.kt` | - | argument-list-wrapping, function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-call-site, trailing-comma-on-declaration-site, wrapping | RN style |
| `NottiEventStoreTest.kt` | - | blank-line-before-declaration, function-signature, multiline-expression-wrapping, no-empty-first-line-in-class-body, statement-wrapping, trailing-comma-on-declaration-site | RN style |
| `NottiInAppModelsTest.kt` | - | argument-list-wrapping, function-signature, multiline-expression-wrapping, trailing-comma-on-call-site, trailing-comma-on-declaration-site | RN style |
| `NottiInAppEventStoreTest.kt` | - | function-signature, no-empty-first-line-in-class-body, trailing-comma-on-declaration-site | RN style |
| `NottiApiClientTest.kt` | - | argument-list-wrapping, blank-line-between-when-conditions, chain-method-continuation, function-signature, import-ordering, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-call-site, trailing-comma-on-declaration-site | RN style |
| `NottiCoreTest.kt` | - | argument-list-wrapping, blank-line-before-declaration, blank-line-between-when-conditions, chain-method-continuation, function-signature, import-ordering, indent, multiline-expression-wrapping, no-empty-first-line-in-class-body, statement-wrapping, trailing-comma-on-call-site, trailing-comma-on-declaration-site, wrapping | RN style |
| `NottiForegroundObserverTest.kt` | - | chain-method-continuation, multiline-expression-wrapping, no-empty-first-line-in-class-body, trailing-comma-on-call-site | RN style |
| `NottiNetworkObserverTest.kt` | - | chain-method-continuation, no-empty-first-line-in-class-body | RN style |

---

## port-diff summary

`scripts/port-diff.sh <rn-file> <ported-file>` (header, `@file:Suppress` block, package/imports, blank lines and whitespace ignored). Numbers are allowed changes of design "Allowed changes in ported files". No `SPEC_DEVIATION`.

| Ported file | Hunks | Nature |
| --- | --- | --- |
| `NottiDeviceStore.kt` | 2 | 3: `DeviceState`, `NottiDeviceStore` internal |
| `NottiEventStore.kt` | 4 | 3: `PendingEvent`, `NottiEventType`, `NottiEventStore` internal; 7: `LOCK` KDoc `NottiModule.getEventStore` → `NottiRuntime.eventStore` |
| `NottiInAppModels.kt` | 8 | 3: nine top-level declarations internal |
| `NottiInAppEventStore.kt` | 3 | 3: `InAppEventType`, `InAppPendingEvent`, `NottiInAppEventStore` internal |
| `NottiInAppHooks.kt` | 1 (`diff -w` vs `NottiInAppCoordinator.kt` 41-55) | 3: internal |
| `NottiApiClient.kt` | 9 | 3: top-level declarations internal; 5: `lastError = e.javaClass.simpleName`, `invalid events request: ${e.javaClass.simpleName}` |
| `NottiCore.kt` | 9 | 3: class internal; 5: four `logger` lines `t.message` → `t.javaClass.simpleName`; 7: comments `NottiModule` → `NottiRuntime` / `NottiAndroidProviders` (KDoc ×4, executor-shutdown comment) |
| `NottiForegroundObserver.kt` (vs `NottiInitProvider.kt` 101-193, manual) | 8 | 6: `coreProvider`/`inAppProvider` → `NottiRuntime.peek()`, `inAppProvider`/`handleProcessBackground` take `NottiInAppLifecycle?`, `gateProvider` + `onStop` close only with a gate (design second amendment); 5: two `Log.e` → `NottiLogger.error` with the class; 7: KDoc |
| `NottiNetworkObserver.kt` | 3 | 3: internal; 6: default callback → `NottiRuntime.peek()` core and `inAppLifecycle`; 5: `Log.e` → `NottiLogger.error`; 7: KDoc |
| `NottiDeviceStoreTest.kt`, `NottiEventStoreTest.kt`, `NottiInAppModelsTest.kt`, `NottiInAppEventStoreTest.kt`, `NottiApiClientTest.kt`, `NottiCoreTest.kt` | 0 | identical |
| `NottiNetworkObserverTest.kt` | 1 | 7: KDoc |
| `NottiForegroundObserverTest.kt` | 5 | 6: `NottiModule` statics → `NottiRuntime.resetForTest()` / `runtime.sessionGate`; In-App coordinator → recording `NottiInAppLifecycle` (deferred list); 7: KDoc |

---

## Storage contract

Every key `NottiDeviceStore` reads or writes in `notti_prefs`, with the `SharedPreferences` type of its `put*`/`get*` calls and the read default, taken from the ported file (identical to RN v0.8.0 `NottiDeviceStore.kt`). A continuity fixture written by RN 0.8.0 must read back with these exact types: a type change throws `ClassCastException` on upgrade.

Conventions: "`-1L` → null" means the getter reads `getLong(key, -1L).takeIf { it >= 0 }` and the setter writes `value ?: -1L` (the key stays present after a clear). String setters write `putString(key, null)`, which removes the key. Writes use `apply()` unless the Write column says `commit()`.

| Key constant | Key | Type | Read default | Write |
| --- | --- | --- | --- | --- |
| `KEY_DEVICE_ID` | `notti_device_id` | String | `null` | apply |
| `KEY_LAST_TOKEN` | `notti_last_token` | String | `null` | apply |
| `KEY_EXTERNAL_USER_ID` | `notti_external_user_id` | String | `null` | apply |
| `KEY_SUBSCRIBED` | `notti_subscribed` | Boolean | `false` (`getSubscribedOrNull`: `null` when absent, via `contains`) | apply |
| `TAG_KEY_PREFIX` | `notti_tag_<key>` | String (one key per tag; read through `getAll()` filtered by prefix) | no tags | apply (`setTags` removes old tag keys, puts new ones, one editor) |
| `KEY_APP_VERSION` | `notti_app_version` | String | `null` | apply; removed by `migrateProfileFieldAckIfNeeded` and `clearProfileFieldAcks` |
| `KEY_FIRST_SESSION_AT` | `notti_first_session_at_ms` | Long | `-1L` → null | apply |
| `KEY_LAST_SESSION_AT` | `notti_last_session_at_ms` | Long | `-1L` → null | apply |
| `KEY_SESSION_COUNT` | `notti_session_count` | **Int** | `0` | apply |
| `KEY_SESSION_TIME_MS` | `notti_session_time_ms` | Long | `0L` | apply |
| `KEY_SESSION_STARTED_AT` | `notti_session_started_at_ms` | Long | `-1L` → null | apply |
| `KEY_LOCATION_SHARING_ENABLED` | `notti_location_sharing_enabled` | Boolean | `false` | **commit()** (LGPD opt-out) |
| `KEY_PENDING_COUNTRY_CLEAR` | `notti_pending_country_clear` | Boolean | `false` | **commit()** |
| `KEY_PENDING_EXTERNAL_USER_ID_CLEAR` | `notti_pending_external_user_id_clear` | Boolean | `false` | **commit()** |
| `KEY_LAST_SYNCED_COUNTRY` | `notti_last_synced_country` | String | `null` | apply |
| `KEY_LAST_FOREGROUND_AT` | `notti_last_foreground_at_ms` | Long | `-1L` → null | apply |
| `KEY_LAST_SYNCED_DEVICE_OS` | `notti_last_synced_device_os` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_DEVICE_MODEL` | `notti_last_synced_device_model` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_SDK_VERSION` | `notti_last_synced_sdk_version` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_TIMEZONE_ID` | `notti_last_synced_timezone_id` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_LANGUAGE` | `notti_last_synced_language` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_PERMISSION_STATUS` | `notti_last_synced_permission_status` | String | `null` | apply; removed by `clearProfileFieldAcks` only |
| `KEY_LAST_UNSUBSCRIBED_AT_MS` | `notti_last_unsubscribed_at_ms` | Long | `-1L` → null | apply |
| `KEY_EMAIL` | `notti_email` | String | `null` | **commit()** (LGPD clear) |
| `KEY_PHONE` | `notti_phone` | String | `null` | **commit()** |
| `KEY_LAST_SYNCED_EMAIL` | `notti_last_synced_email` | String | `null` | **commit()** |
| `KEY_LAST_SYNCED_PHONE` | `notti_last_synced_phone` | String | `null` | **commit()** |
| `KEY_PERMISSION_REQUESTED` | `notti_permission_requested` | Boolean | `false` | apply |
| `KEY_PENDING_UNSUBSCRIBE_AT_MS` | `notti_pending_unsubscribe_at_ms` | Long | `-1L` → null | apply |
| `KEY_PENDING_PERMISSION_UNSUBSCRIBE_AT_MS` | `notti_pending_permission_unsubscribe_at_ms` | Long | `-1L` → null | apply |
| `KEY_PROFILE_FIELD_ACK_MIGRATED` | `notti_profile_field_ack_migrated` | Boolean | `false` | apply (set `true` once by `migrateProfileFieldAckIfNeeded`, in the same editor as its removals) |

Design note: the design's data-model table groups `notti_session_count` with the Long keys; it is an **Int** (`putInt`/`getInt`), the only Int key in the store.
