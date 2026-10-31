# Public API Specification

## Problem Statement

After `core-port` and `push-integration`, the SDK runs on Android but a host app can only reach it through `NottiBridge`: every other type is `internal`. A host cannot initialize the SDK, identify a user, set tags, ask for push permission, listen to notification events or read the device id. RN 0.8.0 exposes these through its JS facade (`src/index.tsx`) over `NottiModule`, and the iOS SDK through its `Notti` facade (`zeep-notti-ios-sdk` `public-api`). This feature gives native Android hosts the same surface as a Kotlin-first facade that is also usable from Java, so that M1 (0.1.0) is usable from an app and M2 can map RN's `NottiModule` onto it.

## Goals

- [x] Every RN 0.8.0 public method outside In-App (`trackEvent`, `InAppMessages`) has a documented Kotlin equivalent, each covered by at least one passing JUnit test that goes through the facade, not through `NottiCore`.
- [x] The facade adds no business logic (AD-001, AD-002). New behavior is limited to what only a facade can own and the iOS SDK already decided (AD-008): listener multiplexing, main-thread delivery, initialize idempotency, log level, default `baseUrl`.
- [x] The SDK works when the host removes `NottiInitProvider`: `initialize` installs the process hooks the provider would have installed (closes the push-integration open item).
- [x] The public surface is exactly what this spec lists, guarded by `apiCheck` (AD-010), and callable from Java (a Java test compiles against it).
- [x] No PII in any log line produced by the facade, at any log level (AD-011).

## Out of Scope

| Feature | Reason |
| --- | --- |
| `Notti.trackEvent`, `Notti.InAppMessages.addClickListener`, the In-App URL-open initialize option (AD-006) | `in-app-messages`, as on iOS (iOS in-app-messages spec, `trackEvent` placement row). Without the In-App engine `trackEvent` has nothing to call |
| FCM service, process hook, tap detection, permission dialog, `NottiBridge` | Done in `push-integration`; this feature only reads its relays |
| RN `NottiModule` on top of the facade | `rn-consumes-native-sdk` (M2) |
| Consent gating, local data wipe | `privacy-consent` (M3) |
| Email/phone out of plain `SharedPreferences` | `pii-secure-storage` (M3) |
| Coroutines/Flow API (`suspend`, `Flow` listeners) | No coroutines in M1 (AD-002); a `kotlinx-coroutines` dependency would be forced on hosts. Callback API only; revisit after M1 |
| Initialized-state accessor, permission-change listener, foreground listener | Not in RN 0.8.0 nor in iOS M1 |
| Detecting a cold-start tap when the host removed `NottiInitProvider` **and** calls `initialize` after the launch Activity's first `onResume` | No hook exists yet when the launch Activity reads its intent. README recommends `initialize` in `Application.onCreate` (see Edge Cases) |

---

## Parity Table (AD-008)

| Capability | RN 0.8.0 (JS facade / `NottiModule`) | iOS SDK | Android (this spec) |
| --- | --- | --- | --- |
| Initialize | `Notti.initialize(appId, clientKey, {baseUrl})`, default `https://app.zeepnotti.app` (TS); same config no-op, different config re-initializes the same core (SDK-07) | `Notti.initialize(appId:clientKey:options:)`, same config no-op, different rebuilds the core | `Notti.initialize(context, appId, clientKey, options)`; RN mechanism, same observable result as iOS (design D1). Needs a `Context` (Android only) |
| Login / logout | `login(id)` / `logout()` | same | same |
| Tags | `User.addTag/addTags/removeTag/removeTags`, non-string values dropped (native) | same, `sanitizeTags` in the ObjC layer | same; Kotlin/Java types are `String` so nothing to drop |
| Email / phone | `User.setEmail/clearEmail/setPhone/clearPhone` | same | same |
| Subscription, location sharing | top-level `setSubscription`, `setLocationSharingEnabled` | `User.setSubscription`, top-level `setLocationSharingEnabled` | follows RN placement: both top level (see Assumptions) |
| Device id | `getDeviceId()` sync; `deviceIdChanged` event | `deviceId` + observer | `Notti.deviceId` + `addDeviceIdChangedListener` |
| Permission | `requestPermission(): Promise<boolean>` | `async` + completion | callback `(Boolean) -> Unit` |
| Received / clicked | `addEventListener('notificationReceived' / 'notificationClicked')`, `EventSubscription` | `Notifications.addReceivedListener/addClickedListener`, `NottiSubscription` | iOS shape |
| Initial click | `getInitialNotificationClick(): Promise` (one-shot) | `takeInitialClick()` | `Notifications.takeInitialClick()`, sync |
| Log level | none (logs always at error) | `Notti.logLevel` | `Notti.logLevel` |
| Callback thread | JS thread (RN) | main | main |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Behavior source | RN 0.8.0 for what the core does; iOS SDK `public-api` for facade-only rules (AD-008) | AD-002, AD-008 | y (AD) |
| Shape | `object Notti` with nested `object User` and `object Notifications`; every method `@JvmStatic`, so Java calls `Notti.User.addTag(...)` | Mirrors RN/iOS naming; one surface for Kotlin and Java (no separate interop layer as iOS needed for ObjC) || y (Julio, 2026-10-30) |
| `Context` in `initialize` | Required first parameter; the SDK keeps only `applicationContext` | The runtime needs it when the provider was removed; RN had `ReactApplicationContext`. The M2 bridge has one || y (Julio, 2026-10-30) |
| Options type | `NottiOptions` class with `baseUrl: String?` (null = default), built in Kotlin with named args and in Java with a builder or no-arg constructor + setter | Room for the AD-006 flag in `in-app-messages` without breaking the ABI (adding a field to a data class breaks `copy`/`componentN`) || y (Julio, 2026-10-30) |
| `initialize` called again | Same normalized `appId`/`clientKey`/`baseUrl`: no-op. Different: register again with the new config | AD-008, iOS API-02; RN already did this inside `NottiCore.initialize` (SDK-07), corrected in design D1 | y (AD-008) |
| Default `baseUrl` | `https://app.zeepnotti.app` when `options.baseUrl` is null | AD-008; RN `SAAS_BASE_URL` | y (AD-008) |
| Placement of `setSubscription` / `setLocationSharingEnabled` | Top level (`Notti.setSubscription`, `Notti.setLocationSharingEnabled`), as RN; iOS put `setSubscription` under `User` | The M2 bridge maps 1:1 and RN docs stay valid. Divergence from iOS listed here || y (Julio, 2026-10-30) |
| Permission API | `Notti.Notifications.requestPermission(callback: (Boolean) -> Unit)`; the `Boolean` is the dialog result as the core reports it (RN `Promise<boolean>`) | No coroutines in M1 || y (Julio, 2026-10-30) |
| Before `initialize` | Exactly RN: RN built its core lazily on the first call of any method, so a call before `initialize` reached an un-initialized core, which queues or drops it by its own rules (pending mutations coalesced and flushed after the first registration; email/phone persisted, DPF-19; `requestPermission` answers `false` without the dialog). The runtime gets an `ensureCore()` that creates that un-initialized core (iOS did the same, `NottiRuntime.ensureCore()`), and `start` initializes it instead of building a second one | AD-002; iOS API-04..08 || y (Julio, 2026-10-30) |
| Runtime before `initialize` | Facade calls use the runtime the provider created (`NottiRuntime.peek()`). With no runtime (provider removed and no `initialize` yet) a call is dropped with a warn line and `deviceId` returns null | No context is available otherwise; no new behavior when the provider is present || y (Julio, 2026-10-30) |
| Failure model | Nothing in the facade throws. Invalid input (blank ids, invalid `baseUrl`) is logged without the values and ignored | Core contract; iOS API-01 | y (inherited) |
| Listener model | Multi-listener, each `add…Listener` returns a `NottiSubscription` with idempotent `remove()`; multiplexed over the runtime's single slot per event | AD-008, RN `EventSubscription` | y (AD-008) |
| Callback thread | Every host callback (listeners, permission callback) runs on the main thread, in emission order per listener | AD-008 | y (AD-008) |
| `received` replay | None: an FCM message that arrives with no listener is not buffered | RN 0.8.0 Android had no buffer (`NottiRuntime.onNotificationReceived` doc). iOS core buffers; Android does not get a buffer in M1 (would be new behavior) || y (Julio, 2026-10-30) |
| Click delivery vs initial click | While at least one clicked listener exists, the click relay is attached and clicks go to the listeners; with none, the relay buffers the last click for `takeInitialClick()` (RN relay semantics). A host that adds the clicked listener in `Application.onCreate` therefore receives the cold-start click as an event, and `takeInitialClick()` returns null | Follows the ported relay unchanged; RN's "not replayed" rule was about JS timing, which native hosts do not have || y (Julio, 2026-10-30) |
| Payload | Public `NottiNotification` with `title: String?`, `body: String?`, `data: Map<String, String>` and nothing else; a plain class (`equals`/`hashCode`/`toString`), not a `data class` | iOS API-10 payload; avoids `copy`/`componentN` in the ABI. `toString` does not print `data` values (may hold PII) || y (Julio, 2026-10-30) |
| Log level | Public `NottiLogLevel` enum (`NONE`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `VERBOSE`), `Notti.logLevel` get/set, default `ERROR`, mapped to the internal `NottiLogger.Level` | iOS API-14; AD-011 | n (iOS confirmed) |
| Hooks without the provider | `initialize` installs, once per process, what `NottiInitProvider.onCreate` installs (lifecycle listener, network observer, foreground observer) if the provider has not run | push-integration open item || y (Julio, 2026-10-30) |

Open questions: none. Proposed rows confirmed by Julio on 2026-10-30.

---

## User Stories

### P1: Initialize and identify ⭐ MVP

**User Story**: As an Android developer, I want to initialize Notti and identify the user with one call each so that the device registers and push targeting works.

**Why P1**: Nothing else works without `initialize`, and identity is the main reason a host adopts the SDK.

**Acceptance Criteria**:

1. WHEN the host calls `Notti.initialize(context, appId, clientKey, options)` THEN the facade SHALL start the process runtime (`NottiRuntime.get(context)`) with those values and `baseUrl` from `options.baseUrl`, or `https://app.zeepnotti.app` when it is null (API-01).
2. WHEN `appId` or `clientKey` is blank or `baseUrl` is invalid THEN the facade SHALL NOT throw, SHALL log through `NottiLogger` without `appId`/`clientKey` (accepted exception AD-011: an invalid `baseUrl` may be logged as given), and SHALL leave the SDK as it was: no registration, and an initialized core keeps its config (API-01).
3. WHEN `initialize` is called again with the same normalized `appId`, `clientKey` and `baseUrl` THEN the SDK SHALL NOT send a second registration. WHEN any of them differs THEN it SHALL register again with the new config (the core re-initializes in place, RN SDK-07/L2; design D1) (API-02).
4. WHEN the facade runs in production THEN it SHALL use the process runtime backed by `notti_prefs`, and a test SHALL prove the facade path reads and writes that file (API-03).
5. WHEN the host calls `Notti.login(externalUserId)` / `Notti.logout()` THEN the facade SHALL call `core.login` / `core.logout`; persistence, pending-mutation queueing and server-side clearing of email/phone SHALL stay exactly the core's (API-04).
6. WHEN the host reads `Notti.deviceId` THEN it SHALL receive the persisted id synchronously, also before `initialize`, or null if the device never registered or no runtime exists (API-05).
7. WHEN the device id changes THEN every device-id listener SHALL be called once with the new id on the main thread; a re-registration with the same id SHALL NOT call them (API-05, API-11).

**Independent Test**: A facade test on an isolated runtime with MockWebServer initializes, asserts one `POST /devices`, initializes again with the same and then a different config and asserts 0 and 1 more registrations, then logs in and asserts the PATCH.

---

### P1: User profile

**User Story**: As an Android developer, I want to set tags, email, phone, subscription and location sharing so that the backend can segment and reach the user.

**Why P1**: The rest of the RN `Notti.User` surface and the main targeting input.

**Acceptance Criteria**:

1. WHEN the host calls `Notti.User.addTag(key, value)`, `addTags(map)`, `removeTag(key)` or `removeTags(keys)` THEN the facade SHALL map them to `core.mutateTags(add, remove)` exactly as `NottiModule` does, keeping the core's merge-at-send behavior (API-06).
2. WHEN the host calls `setEmail`, `clearEmail`, `setPhone` or `clearPhone`, also before `initialize` while a runtime exists, THEN the value SHALL be persisted and converge at the next registration, as the core does (persist-before-guard, coalescing, explicit-null clear) (API-07).
3. WHEN the host calls `Notti.setSubscription(enabled)` THEN the facade SHALL call `core.setSubscription`, including the `last_unsubscribed_at` rule (API-08).
4. WHEN the host calls `Notti.setLocationSharingEnabled(enabled)` THEN the preference SHALL be written synchronously on the caller's thread; the default SHALL stay `false` and the SDK SHALL NOT request location permission (API-08).
5. WHEN any profile call happens before `initialize` THEN behavior SHALL be the core's, with no exception (API-04, API-06..08).

**Independent Test**: One facade test per method on an initialized isolated runtime asserting the PATCH body fields, and one per method before `initialize`.

---

### P1: Notifications

**User Story**: As an Android developer, I want to ask for push permission and receive notification events so that I can drive UI from them.

**Why P1**: Permission and events are the other pillars of the RN surface.

**Acceptance Criteria**:

1. WHEN the host calls `Notti.Notifications.requestPermission(callback)` THEN the callback SHALL receive the core's result once, on the main thread. WHEN called before `initialize` THEN it SHALL receive `false` without the dialog (API-09).
2. WHEN the host calls `Notti.Notifications.addReceivedListener`, `addClickedListener` or `Notti.addDeviceIdChangedListener` THEN it SHALL get a `NottiSubscription`; after `remove()` that listener SHALL NOT be called again, and a second `remove()` SHALL be a no-op (API-10).
3. WHEN several listeners exist for one event THEN each SHALL receive every event, in registration order, on the main thread. WHEN the last clicked listener is removed THEN the click relay SHALL be detached so later clicks are buffered for `takeInitialClick()` (API-10, API-11).
4. WHEN a listener adds or removes a listener from inside a callback THEN the facade SHALL NOT deadlock or throw, and the change SHALL apply from the next event (API-10).
5. WHEN the host calls `Notti.Notifications.takeInitialClick()` THEN it SHALL return the buffered click once and null afterwards (RN `getInitialNotificationClick`) (API-12).
6. WHEN a payload is delivered THEN it SHALL be a `NottiNotification` with `title`, `body` and `data` only, with `data` exactly the FCM data map (API-10, API-12).
7. WHEN a listener throws THEN the facade SHALL log the exception class (not its message) and still deliver to the remaining listeners (API-10).

**Independent Test**: Tests drive the runtime's received slot and the click relay with synthetic payloads (`example.invalid`, `fixture`), add two listeners, remove one, and assert counts, order and thread (Robolectric main looper).

---

### P1: Works without the process hook

**User Story**: As an Android developer who removed `NottiInitProvider` from my manifest, I want `initialize` to set the SDK up anyway so that foreground sessions, event flushing and warm/hot tap detection still work.

**Why P1**: AD-005 documents the opt-out; without this the SDK silently loses features (push-integration open item).

**Acceptance Criteria**:

1. WHEN `initialize` runs and the provider did not run in this process THEN the facade SHALL install the Activity lifecycle listener, the network observer and the foreground observer exactly once (API-13).
2. WHEN the provider did run THEN `initialize` SHALL NOT install them a second time (API-13).
3. WHEN the hooks are installed late THEN a later warm or hot tap SHALL be detected and the current foreground SHALL count as a session, as with the provider (API-13).

**Independent Test**: A Robolectric test with no provider calls `initialize`, then asserts one registration of each hook, and a second `initialize` adds none.

---

### P1: Public surface hygiene

**User Story**: As the SDK maintainer, I want the public surface small, guarded and usable from Java so that 0.1.0 does not freeze accidental API.

**Why P1**: Public symbols are a compatibility promise (AD-010).

**Acceptance Criteria**:

1. WHEN the module builds THEN `notti/api/notti.api` SHALL list only `NottiBridge` and the symbols this spec defines, and `apiCheck` SHALL pass (API-14).
2. WHEN a Java source in the test set calls every public method (initialize, login, tags, email, listeners with lambdas, `requestPermission`, `logLevel`, `NottiOptions`) THEN it SHALL compile and run (API-14).
3. WHEN public types mirror internal ones (`NottiLogLevel` / `NottiLogger.Level`, `NottiNotification` / `ParsedNotification`) THEN no internal type SHALL appear in a public signature (API-14).

**Independent Test**: `apiCheck` green; the Java test runs in `testDebugUnitTest`.

---

### P2: Log level

**User Story**: As an Android developer, I want to raise the SDK log level while debugging without leaking user data.

**Why P2**: Useful for integration, not needed for function.

**Acceptance Criteria**:

1. WHEN the host sets `Notti.logLevel` THEN `NottiLogger.level` SHALL change accordingly and the getter SHALL return it; default `ERROR` (API-15).
2. WHEN the level is `VERBOSE` THEN the existing log-privacy sentinel test SHALL pass through the facade: no email, phone, push token, external user id, country, tag values or URLs in any line (API-15).

**Independent Test**: Set `VERBOSE`, run a facade flow with sentinel values, assert none reaches the sink.

---

## Edge Cases

- WHEN `initialize` is called from a background thread THEN behavior SHALL equal a main-thread call, and callbacks SHALL still arrive on main.
- WHEN two `initialize` calls with different configs race THEN the final state SHALL be one live core with no leaked executor (`NottiRuntime.start` is synchronized).
- WHEN a listener is added before `initialize` THEN it SHALL work once events flow (the relays exist before any core).
- WHEN `requestPermission` is called twice before the first answers THEN both callbacks SHALL be called, each once.
- WHEN a subscription is kept after `remove()` THEN it SHALL hold no reference to the listener.
- WHEN the provider was removed and the app was cold-started by a tap THEN: with `initialize` in `Application.onCreate` the tap SHALL be detected as with the provider; with `initialize` in the launch Activity's `onCreate` it SHALL be detected on `onActivityResumed`, and the replay guards that today run only on `onActivityCreated` (`savedInstanceState`, `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) SHALL also apply so a Recents reopen or process restore does not count twice; with `initialize` after the first `onResume` the tap SHALL NOT be detected. The README states the three cases (example-and-release).
- WHEN `initialize` receives a non-application `Context` (an Activity) THEN the SDK SHALL keep only `applicationContext` (no Activity leak).

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| API-01 | P1: Initialize and identify (initialize, default baseUrl, no throw) | Execute | Done |
| API-02 | P1: Initialize and identify (idempotency) | Execute | Done |
| API-03 | P1: Initialize and identify (production runtime on `notti_prefs`) | Execute | Done |
| API-04 | P1: Initialize and identify (login/logout) | Execute | Done |
| API-05 | P1: Initialize and identify (device id accessor and listener) | Execute | Done |
| API-06 | P1: User profile (tags) | Execute | Done |
| API-07 | P1: User profile (email, phone) | Execute | Done |
| API-08 | P1: User profile (subscription, location sharing) | Execute | Done |
| API-09 | P1: Notifications (requestPermission) | Execute | Done |
| API-10 | P1: Notifications (listeners, payload, isolation) | Execute | Done |
| API-11 | P1: Notifications / identify (main-thread delivery, relay detach) | Execute | Done |
| API-12 | P1: Notifications (initial click) | Execute | Done |
| API-13 | P1: Works without the process hook | Execute | Done |
| API-14 | P1: Public surface hygiene (ABI, Java, no internal types) | Execute | Done |
| API-15 | P2: Log level (+ no PII at all levels) | Execute | Done |

Inherited behavior (facade part only; core part verified in `core-port`/`push-integration`): RN `sdk-core-v1` SDK-17..19 (API-05, API-10, API-12), `opened-event-reporting` SDKOPEN-10 (API-10), `device-profile-fields` DPF-17..21 (API-07).

**Coverage:** 15 total, 15 mapped to a story, 15 mapped to tasks (`tasks.md`), 15 mapped to tests (`traceability.md`).

---

## Success Criteria

- [x] Every RN 0.8.0 public method outside In-App has a facade equivalent, listed with its test in `traceability.md`.
- [x] `apiCheck` green with the new dump; the Java call test green.
- [x] Privacy sentinel test green at `VERBOSE` through the facade.
- [x] Test count does not drop below 390; full gate (tests, `spotlessCheck`, `lintRelease`, `apiCheck`, guard scripts, publish + example build) green.
