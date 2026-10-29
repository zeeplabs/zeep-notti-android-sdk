# Core Port Specification

## Problem Statement

All of Notti's Android core behavior (registration, identity, tags, profile fields, session telemetry, engagement events, retry and the offline queue) lives in `zeep-notti-react-native/android`, wired to the app through the TurboModule `NottiModule`: its companion statics (`activeCore`, `processSessionGate`, the process-wide `eventStore`), its `ReactApplicationContext` and the Android platform readers it passes into `NottiCore` (app version, OS, model, timezone, language, permission status, location check, country, FCM token). AD-001 makes this repo the single source of truth. The core has to move here unchanged in behavior, without React Native, with its JUnit suite, so that `public-api`, `push-integration` and `in-app-messages` build on a proven engine and M2 can delete the RN copy.

## Goals

- [ ] Behavior parity: every Android-applicable core requirement of the RN specs (`SDK-`, `SDKCTR-`, `SDKOPEN-`, `SEGTEL-`, `DPF-`, `DLOG-`) keeps its behavior and has at least one passing test here that exercises the layer implementing it.
- [ ] Zero React Native symbols in `notti/src` (no `com.facebook.react`, `ReactApplicationContext`, `WritableMap`, `Arguments`, `Promise`, `NativeNottiSpec`).
- [ ] On-device continuity: same `SharedPreferences` file `notti_prefs`, same keys, same value types and encodings as RN 0.8.0, so a device moving from `react-native-notti` 0.8.0 keeps its device id, tags, session counters, pending queue, acks and pending flags.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Public facade (`Notti.initialize`, `Notti.User`, listeners, `trackEvent`) | `public-api`. This feature exposes `internal` core API only |
| `NottiFirebaseMessagingService`, `NottiInitProvider`, `NottiActivityLifecycleListener`, `NottiNotificationClickRelay`, `NottiBridge`, `parseRemoteMessage`, manifest entries | `push-integration`. This feature ports the core entry points they call (`onTokenRefreshed`, event enqueue, foreground/background), not who calls them |
| Runtime permission request (`POST_NOTIFICATIONS` dialog, needs an Activity) | `push-integration`; the core takes the requester as an injected function, as in RN |
| In-App engine (`NottiInApp*`) | `in-app-messages`. Only the `NottiInAppHooks` seam the core calls is ported here, with its no-op default |
| Any behavior change (Retry-After, queue TTL, secure storage, consent) | AD-002; M3 |
| Coroutines/Flow rewrite | AD-002 |
| Backend contract changes | Contract fixed at `zeep-notti` >= v0.11.0 (>= v0.24.0 for the logout unlink) |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Behavior source | RN tag `v0.8.0` (`291d5a7`); RN `main` has no `android/` change after it (checked 2026-10-28) | AD-002 | y |
| Requirement IDs | Reuse the RN IDs as the behavior spec; this spec adds only `CORE-` IDs for port concerns | Same as iOS core-port | y |
| Companion statics | Replaced by one SDK-owned process container (`NottiRuntime`, name final in Design) holding the active core, the session gate and the event store; cold-start safe (an event enqueued before any core exists is persisted and flushed later) | Statics are the RN coupling; iOS did the same | n - proposed in `design.md` (`NottiRuntime`) |
| Platform providers | The readers that live in `NottiModule` today (app version, OS, model, timezone, language, permission status, location permission check, country, FCM token) move into SDK code with their own tests | They are core behavior (DPF, SEGTEL), not RN bridge code | n - proposed in `design.md` (`NottiAndroidProviders`) |
| `NottiForegroundObserver` | Ported here (session start/end, 60 s heartbeat, cold-start sync); registering it with `ProcessLifecycleOwner` stays with the process hook in `push-integration` | SEGTEL behavior lives in it; the hook is integration | n - proposed in `design.md` |
| `sdk_version` | From `NottiSdkVersion.CURRENT`, never from a caller; RN JS passed its package version | Same as iOS CORE-05. The backend will see `0.1.0` from native apps; the RN-on-native value is an M2 decision | y (iOS precedent) |
| Logging | One internal logger replaces RN's `Log.e(NAME, …)` lambda; no PII; default level error; level API in `public-api` | AD-011 | y |
| Threading | Single-thread executors (`notti-io`, `notti-geo`), blocking OkHttp with retry/backoff, as RN | AD-002 | y |
| `NottiInAppHooks` | Interface extracted from `NottiInAppCoordinator.kt` into its own file with `None`; the core keeps its three call sites | Lets the core compile without the In-App engine | y (port of an existing seam) |

---

## User Stories

### P1: Core engine runs without React Native ⭐ MVP

**User Story**: As the SDK maintainer, I want the Notti core to compile and run inside `:notti` with no RN dependency, so that native apps and the future RN bridge share one engine.

**Acceptance Criteria**:

1. WHEN `:notti` is built THEN it SHALL contain the ported `NottiCore` (with `SessionGate`), `NottiApiClient`, `NottiDeviceStore`, `NottiEventStore` (and `NottiEventType`), `NottiNetworkObserver`, `NottiForegroundObserver` and the `NottiInAppHooks` seam, all `internal`, under `com.zeeplabs.notti`, and SHALL NOT reference any React Native symbol (CI grep).
2. WHEN the core is constructed THEN every dependency SHALL stay injectable as in RN (prefs, API client factory, executor, clock, every provider, session gate, In-App hooks, logger), so tests run on the JVM.
3. WHEN code outside the core (event detection sites, network and foreground observers) needs the active core, the session gate or the event store THEN it SHALL reach them through the SDK-owned container, not through statics spread across types; the container SHALL keep RN's identity rules (a torn-down core never unhooks its replacement).
4. WHEN a received/opened/clicked event is enqueued before any core exists THEN it SHALL be persisted to `notti_pending_events` and flushed after the next registration, network regain or foreground, as RN does (SDKCTR-02, SDKCTR-12).
5. WHEN `sdk_version` is sent THEN it SHALL come from `NottiSdkVersion.CURRENT`; no caller parameter can override it.

**Independent Test**: `:notti:testDebugUnitTest` green; `grep -rE 'com\.facebook\.react|ReactApplicationContext|WritableMap' notti/src` is empty.

---

### P1: Android platform providers ⭐ MVP

**User Story**: As the maintainer, I want the device and OS readers the RN module supplies to the core to live in the SDK, so that registration and profile fields work without the RN module.

**Acceptance Criteria**:

1. WHEN the runtime builds the core THEN it SHALL wire providers with the RN 0.8.0 behavior: app version (`PackageInfo.versionName`), OS (`Build.VERSION.RELEASE`), model (`Build.MODEL`), timezone id, language (RN `normalizeLanguage`), permission status (`granted`/`denied`/`notDetermined` or null, RN rules incl. below API 33), location permission check (read only, never prompts), country (opt-in reverse geocode on `notti-geo`), FCM token (`FirebaseMessaging.getToken`).
2. WHEN a provider fails or its source is unavailable THEN it SHALL return `null` (omit the field), never throw into the core, as RN does.
3. WHEN these providers are tested THEN each SHALL have unit tests (Robolectric where it needs `Context`/`PackageManager`), since RN has none for them (new code paths in this repo).

**Independent Test**: provider tests green with fakes for `PackageManager`, locale, timezone and Firebase.

---

### P1: Behavior parity proven by tests ⭐ MVP

**User Story**: As the maintainer, I want the RN JUnit suite for the ported files ported and green, so that I can prove the port changed no behavior.

**Acceptance Criteria**:

1. WHEN the ported suite runs THEN every RN test for a ported file SHALL exist here and pass, with counts at least RN's: `NottiCoreTest` 128, `NottiApiClientTest` 48, `NottiDeviceStoreTest` 20, `NottiEventStoreTest` 7, `NottiForegroundObserverTest` 3, `NottiNetworkObserverTest` 2 (208). Tests dropped as RN-only SHALL be listed with a reason.
2. WHEN the traceability table is complete THEN every Android-applicable core RN requirement ID SHALL map to at least one test here, and IDs owned by another feature SHALL name it.
3. WHEN an RN requirement was recorded with indirect coverage only (sdk-core-v1 validation: SDK-04, SDK-08, SDK-12, SDK-13, SDK-17) THEN this port SHALL add a direct test for its core-layer part or name the feature that owns the rest.

**Independent Test**: test counts per class from the JUnit XML; `traceability.md` has no gap.

---

### P1: On-device continuity with RN 0.8.0 ⭐ MVP

**User Story**: As an app team moving from `react-native-notti` 0.8.0 (or, in M2, RN onto this SDK), I want the device's persisted Notti state kept, so that users are not duplicated as new devices and pending events are not lost.

**Acceptance Criteria**:

1. WHEN the core reads or writes persisted state THEN it SHALL use `SharedPreferences` `notti_prefs` (`MODE_PRIVATE`) with exactly the RN 0.8.0 key names and value types (String/Long/Int/Boolean as RN writes them) and encodings (the `notti_pending_events` JSON format, the `notti_tag_` prefix).
2. WHEN a `notti_prefs` pre-populated with RN 0.8.0 state (device id, last token, tags, session counters, pending events, profile acks, `notti_profile_field_ack_migrated`, pending logout clear) is loaded THEN the core SHALL keep that device id and SHALL NOT `POST` a new registration unless RN 0.8.0 would in the same state.
3. WHEN a fixture of RN 0.8.0 state (synthetic values only) is checked in THEN a test SHALL load it and assert the above, and a golden test SHALL pin the production file name `notti_prefs` (lesson: production-only identifiers need their own assertion).

**Independent Test**: fixture tests green. Device check (example-and-release): install the RN example 0.8.0, register, install a native build with the same `applicationId`; same device id on the backend.

---

### P2: No PII in logs

**User Story**: As an integrator under LGPD, I want the SDK's logs never to carry email, phone, push token, country, external user id, tag values or URLs.

**Acceptance Criteria**:

1. WHEN the core logs THEN it SHALL go through one internal logger; the ported `Log.e` call sites and the core's `logger` lambda SHALL be routed to it, and no message SHALL interpolate those values.
2. WHEN the logger is in its default configuration THEN it SHALL emit only errors.
3. WHEN a test runs register → login → setEmail → setPhone → addTags → event flush with sentinel values THEN no sentinel SHALL appear in captured log output.

**Independent Test**: the sentinel test.

---

## Edge Cases

- WHEN `notti_pending_events` holds JSON RN cannot decode THEN the port SHALL behave as RN 0.8.0 (Design records the exact behavior from the RN code and pins it with a test).
- WHEN a second core is created in the same process (re-initialize) THEN the shared session gate SHALL prevent a second session for the same foreground, as with an RN reload (SEGTEL notes).
- WHEN the app is backgrounded while no core exists THEN the gate SHALL still close (RN `handleProcessBackground`).
- WHEN `baseUrl` is not `https` THEN it SHALL be accepted or rejected exactly as RN `NottiCore.normalizeBaseUrl` does.
- WHEN FCM is not configured in the host (no `google-services.json`) THEN the token provider SHALL return `null` without crashing and the core SHALL behave as RN 0.8.0 does with a null token. RN never tested this path (SDK-04 is "Needs Fix" in its validation); this port adds the direct test (CORE-11).

---

## Requirement Traceability

Port-specific requirements:

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| CORE-01 | P1 Core without RN: components ported, internal, no RN symbols | Tasks | Mapped |
| CORE-02 | P1 Core without RN: all dependencies injectable | Tasks | Mapped |
| CORE-03 | P1 Core without RN: container replaces companion statics | Tasks | Mapped |
| CORE-04 | P1 Core without RN: pre-core events persisted and flushed | Tasks | Mapped |
| CORE-05 | P1 Core without RN: native `sdk_version` | Tasks | Mapped |
| CORE-06 | P1 Providers: RN 0.8.0 behavior for each reader | Tasks | Mapped |
| CORE-07 | P1 Providers: failure returns null | Tasks | Mapped |
| CORE-08 | P1 Providers: unit tests | Tasks | Mapped |
| CORE-09 | P1 Parity: suite ported, counts, drop list | Tasks | Mapped |
| CORE-10 | P1 Parity: every applicable RN ID mapped | Tasks | Mapped |
| CORE-11 | P1 Parity: direct tests for SDK-04/08/12/13/17 core parts | Tasks | Mapped |
| CORE-12 | P1 Continuity: same file, keys, types, encodings | Tasks | Mapped |
| CORE-13 | P1 Continuity: no re-register on migrated state | Tasks | Mapped |
| CORE-14 | P1 Continuity: fixture and golden file-name tests | Tasks | Mapped |
| CORE-15 | P2 Logs: single logger, no PII | Tasks | Mapped |
| CORE-16 | P2 Logs: errors-only default | Tasks | Mapped |
| CORE-17 | P2 Logs: sentinel test | Tasks | Mapped |

Inherited behavior requirements (verified through CORE-10; test-by-test mapping in `traceability.md` at Tasks):

- `sdk-core-v1` (SDK-01..19): core parts here. SDK-11 (`POST_NOTIFICATIONS` on API 33+) and the click/receive wiring → `push-integration`; listener/emitter parts (SDK-17..19) → `public-api`
- `ctr-event-reporting` (SDKCTR-01..14): queue, flush and reporting here; detection sites → `push-integration`
- `opened-event-reporting` (SDKOPEN-01..11): reporting here; tap detection → `push-integration`; JS events and docs → `public-api`, `example-and-release`
- `segment-telemetry-reporting` (SEGTEL-01..15): all here (Android variants: `ProcessLifecycleOwner` foreground, `notti_last_foreground_at_ms` heartbeat)
- `device-profile-fields` (DPF-01..21): all core logic here; JS wording → `public-api`
- `logout-clears-external-id` (DLOG-01..07) and the RN 0.8.0 login/logout coalescing (#41): all here

**Coverage:** 17 port requirements, 17 mapped to tasks (`tasks.md`).

---

## Success Criteria

- [ ] Ported suite green, at least 208 tests from the six RN classes plus the new provider, container, continuity and log tests.
- [ ] `traceability.md` shows no unmapped Android-applicable core RN ID.
- [ ] The RN 0.8.0 state fixture test passes with no duplicate registration.
- [ ] No React Native symbol in `notti/src`.
