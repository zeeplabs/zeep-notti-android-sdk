# Push Integration Specification

## Problem Statement

After `core-port`, the core runs without React Native but nothing connects it to Android: no FCM service receives messages or token refreshes, nothing installs the process-start hooks (runtime, foreground and network observers), nothing detects a notification tap, and the permission dialog and the current-Activity read that `NottiAndroidProviders` needs have only null defaults (so `permission_status` is omitted on API 33+). In RN 0.8.0 this lives in `NottiFirebaseMessagingService`, `NottiInitProvider`, `NottiActivityLifecycleListener`, `NottiNotificationClickRelay`, `NottiBridge`, `parseRemoteMessage` and `NottiModule.requestNativePermission`. This feature ports them onto `NottiRuntime`, so that a native app gets push, events and cold-start clicks with no `FirebaseMessagingService` or Activity code (AD-005), and a host that owns its FCM service can forward manually. Android counterpart of the iOS `delegate-integration`.

## Goals

- [ ] Zero-code path: with only the dependency and `google-services.json`, the merged manifest brings the FCM service and the process hook; FCM token refresh reaches the core; a foreground message reports `received`; a tap (cold, warm or hot) reports `opened` + `clicked` once and buffers the cold-start click.
- [ ] Manual path: a host with its own `FirebaseMessagingService` removes ours from the manifest and forwards through `NottiBridge` with the same results.
- [ ] Permission on API 33+: `requestPermission` shows the system dialog from the current Activity and `permission_status` regains RN parity (closes the core-port temporary gap).
- [ ] Behavior parity with RN 0.8.0 for every ported component, proven by its RN tests.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Public facade, listeners API, `getInitialNotificationClick` as public API, device-id observer | `public-api`; this feature provides the internal relays they read |
| Building or displaying notifications, rich media, action buttons | The system tray displays FCM `notification` payloads (PROJECT scope); M3 |
| Showing a notification while the app is in the foreground | RN 0.8.0 only reports `received`; deferred idea in STATE |
| Detecting a warm tap without `setIntent` in a `singleTop`/`singleTask` host | RN 0.8.0 requires the host to call `setIntent` in `onNewIntent` (RN README); see Assumptions |
| `received` for messages handled by the system tray in background/killed state | Not delivered to `onMessageReceived` by FCM; same as RN |
| In-App setup in the process hook (`NottiInAppSetup.install`) | `in-app-messages` adds it to the hook |
| Expo plugin, notification icon/color meta-data | Host configuration, documented in `example-and-release` README |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Behavior source | RN tag `v0.8.0` (`291d5a7`) | AD-002 | y |
| Process hook | Manifest `ContentProvider` (RN `NottiInitProvider`), not `androidx.startup` | Port; no extra dependency; a host can still remove it with `tools:node="remove"` (AD-005) | n - confirm in Design |
| Provider authority | Distinct from RN's `${applicationId}.nottiinitprovider` | Two providers with one authority fail installation while RN and the SDK coexist (M2, brownfield) | n - name in Design |
| FCM service | Manifest-merged `NottiFirebaseMessagingService` (`MESSAGING_EVENT`, not exported) | RN model, AD-005 | y (AD-005 proposed) |
| Permission request without RN's `PermissionAwareActivity` | SDK-owned mechanism that works from any host Activity (options: a translucent trampoline Activity, or `ActivityResultRegistry` on a `ComponentActivity`) | RN relied on an RN-only interface | n - Design |
| Current Activity | Tracked by the SDK's lifecycle callbacks (last resumed, weak reference) | Needed by `permissionStatus` and the permission request | n - Design |
| `onNewIntent` without `setIntent` | Kept as RN (host calls `setIntent`); README documents it | Port rule; `ComponentActivity.addOnNewIntentListener` could remove the need, recorded as a post-M1 idea | y (parity) |
| Received/clicked delivery to the host | Internal relays (click relay with a one-slot cold-start buffer as RN; received with no buffer as RN) consumed by `public-api` | RN semantics; facade belongs to `public-api` | y |
| `POST_NOTIFICATIONS` | Declared in the library manifest | RN does | y |

---

## User Stories

### P1: FCM messages and token reach the core ⭐ MVP

**User Story**: As a native app developer, I want FCM messages and token refreshes handled by the SDK without writing a `FirebaseMessagingService`.

**Acceptance Criteria**:

1. WHEN the host merges the library manifest THEN it SHALL contain `NottiFirebaseMessagingService` with the `com.google.firebase.MESSAGING_EVENT` intent filter, `exported="false"`, and the `POST_NOTIFICATIONS` permission.
2. WHEN FCM calls `onNewToken` THEN the token SHALL reach the live core's `onTokenRefreshed`; with no live core it SHALL be dropped as RN does (the next `initialize` fetches the token).
3. WHEN FCM calls `onMessageReceived` THEN the message SHALL be parsed as RN `parseRemoteMessage` (title, body, data), handed to the received relay, and, when `notification_id` and `delivery_id` are both non-blank, a `received` event SHALL be enqueued through `NottiRuntime.enqueueEvent` (persisted even with no core).
4. WHEN the message lacks either id THEN no event SHALL be enqueued (SDKCTR parity).

**Independent Test**: ported `NottiFirebaseMessagingServiceTest` (6) green.

---

### P1: Process-start hook ⭐ MVP

**User Story**: As the maintainer, I want the SDK's process-wide hooks installed before any Activity, with no I/O, so cold-start clicks and sessions are never missed.

**Acceptance Criteria**:

1. WHEN the process starts THEN the SDK's `ContentProvider` SHALL create the runtime (`NottiRuntime.get(application)`), register the activity lifecycle listener once, register the network observer, and add `NottiForegroundObserver` to `ProcessLifecycleOwner` posted to the main looper, as RN `NottiInitProvider` does.
2. WHEN the hook runs THEN it SHALL do no network or SDK work beyond constructing these objects; reading `notti_prefs` lazily is allowed as in RN; a failure in any step SHALL be logged (no exception message, AD-011) and SHALL NOT crash the host.
3. WHEN the library manifest is merged THEN the provider SHALL be `exported="false"`, with an authority that cannot collide with RN 0.8.0's.
4. WHEN a host removes the provider (`tools:node="remove"`) THEN the README path (`example-and-release`) SHALL say what is lost (cold-start click, foreground session before `initialize`) and the runtime SHALL still be created lazily on `initialize`.

**Independent Test**: provider test (Robolectric) asserting each registration and no network request (MockWebServer receives nothing).

---

### P1: Notification tap detection ⭐ MVP

**User Story**: As an app developer, I want a tap on a Notti notification to report `opened` and `clicked` and reach my click listener, including when the tap launched the app.

**Acceptance Criteria**:

1. WHEN an Activity is created (no saved state, not launched from history) or resumed with an intent carrying `google.message_id` THEN the SDK SHALL parse it as RN `parseClickIntentExtras` (FCM internal extras dropped, `gcm.n.title`/`gcm.n.body` preferred) and handle each intent instance once.
2. WHEN a tap is handled THEN it SHALL go to the click relay (emitted to an attached consumer, else buffered as the single pending cold-start click, taken once), and, with both SDK ids present, enqueue exactly one `opened` and one `clicked` (SDKOPEN, SDKCTR).
3. WHEN the same tap is seen by `onActivityCreated` and `onActivityResumed` THEN only one pair SHALL be enqueued; a second tap delivering a new intent SHALL enqueue a second pair.
4. WHEN a tap carries a URL or deep link in its data THEN the SDK SHALL pass the data through untouched and SHALL NOT open it (counterpart of the iOS SDK's AD-010 D4; a CI guard bans opening URLs outside the allowlisted files).

**Independent Test**: ported `NottiActivityLifecycleListenerTest` (20) green; relay tests.

---

### P1: Manual forwarding for hosts with their own FCM service ⭐ MVP

**User Story**: As a developer whose app already has a `FirebaseMessagingService` (another push SDK, own pipeline), I want to forward FCM callbacks to Notti.

**Acceptance Criteria**:

1. WHEN the host calls `NottiBridge.onMessageReceived(remoteMessage)` / `NottiBridge.onNewToken(token)` THEN the result SHALL equal the manifest service's (P1 FCM stories), and both SHALL be safe no-ops with no runtime or core.
2. WHEN `NottiBridge` is compiled THEN it SHALL be public, Java-callable (`@JvmStatic`) and listed in the ABI dump (first public API of the SDK; final naming confirmed with `public-api`).
3. WHEN the host removes `NottiFirebaseMessagingService` with `tools:node="remove"` THEN the build SHALL succeed and the bridge path SHALL work (example or test app proof).

**Independent Test**: ported `NottiBridgeTest` (2) green, plus a merged-manifest test with the service removed.

---

### P1: Permission request and status on API 33+ ⭐ MVP

**User Story**: As an app developer, I want `requestPermission` to show the Android 13+ notification dialog from my current screen and the reported `permission_status` to match RN.

**Acceptance Criteria**:

1. WHEN API < 33 THEN the requester SHALL resolve `true` without touching any Activity (RN `NottiModuleTest` case).
2. WHEN API ≥ 33 and an Activity is resumed THEN the SDK SHALL set `notti_permission_requested`, show the system `POST_NOTIFICATIONS` dialog, and resolve with the user's answer; concurrent requests SHALL each resolve exactly once.
3. WHEN API ≥ 33 and no Activity is resumed THEN it SHALL resolve `false` without crashing (RN parity).
4. WHEN the runtime starts a core THEN it SHALL wire this requester and the current-Activity tracker into `NottiAndroidProviders`, so `permission_status` follows RN `mapPermissionStatus` with a real rationale read.

**Independent Test**: Robolectric tests on API 32 and 34 (`ShadowActivity` permission results), including no Activity.

---

## Edge Cases

- WHEN the host Activity is `singleTop`/`singleTask` and does not call `setIntent` in `onNewIntent` THEN a warm tap is not detected (RN parity, documented).
- WHEN a tap intent has extras with null values THEN parsing SHALL skip them without crashing (RN test).
- WHEN a non-Notti push (no SDK ids) is tapped THEN the click SHALL still reach the relay but no event is enqueued (RN).
- WHEN the runtime is created by the FCM service before the provider (provider removed) THEN events SHALL still persist (lazy runtime).
- WHEN two Activities resume in quick succession THEN the current-Activity tracker SHALL hold the last resumed and drop destroyed ones (weak reference, no leak).

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| PUSH-01 | FCM: manifest service, not exported, POST_NOTIFICATIONS | Design | Pending |
| PUSH-02 | FCM: token refresh to the live core | Design | Pending |
| PUSH-03 | FCM: parse, received relay, received event | Design | Pending |
| PUSH-04 | FCM: no event without both ids | Design | Pending |
| PUSH-05 | Hook: registrations at process start | Design | Pending |
| PUSH-06 | Hook: no I/O beyond construction, failures logged, no crash | Design | Pending |
| PUSH-07 | Hook: provider not exported, non-colliding authority | Design | Pending |
| PUSH-08 | Hook: removed provider documented, lazy runtime still works | Design | Pending |
| PUSH-09 | Tap: parse and handle each intent once | Design | Pending |
| PUSH-10 | Tap: click relay buffer and opened + clicked | Design | Pending |
| PUSH-11 | Tap: created/resumed dedupe, new intent re-reports | Design | Pending |
| PUSH-12 | Tap: data untouched, no URL opened (CI guard) | Design | Pending |
| PUSH-13 | Bridge: same results, safe no-ops | Design | Pending |
| PUSH-14 | Bridge: public, Java-callable, in the ABI dump | Design | Pending |
| PUSH-15 | Bridge: service removable from the manifest | Design | Pending |
| PUSH-16 | Permission: API < 33 resolves true | Design | Pending |
| PUSH-17 | Permission: API ≥ 33 dialog, flag, single resolution | Design | Pending |
| PUSH-18 | Permission: no Activity resolves false | Design | Pending |
| PUSH-19 | Permission: runtime wires requester and Activity tracker | Design | Pending |

Inherited RN IDs owned here (named in `core-port/traceability.md`): SDK-11 and the detection/receive parts of SDK-04/08/17, SDKCTR (detection sites), SDKOPEN-01..04 detection, SEGTEL foreground hook registration.

**Coverage:** 19 requirements, 0 mapped to tasks.

---

## Success Criteria

- [ ] Ported tests green: `NottiActivityLifecycleListenerTest` 20, `NottiFirebaseMessagingServiceTest` 6, `NottiBridgeTest` 2, plus the RN module's API < 33 permission case.
- [ ] Merged-manifest checks (service, provider, permission, authority) and a URL-open guard in CI.
- [ ] `permission_status` sent on API 33+ again, matching RN.
- [ ] Example app (placeholder) receives a push and reports events on a device (full device checklist in `example-and-release`).
