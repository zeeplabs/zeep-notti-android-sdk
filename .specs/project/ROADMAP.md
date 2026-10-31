# Roadmap

**Current Milestone:** M1 - Native SDK with RN 0.8.0 parity
**Status:** In Progress

---

## M1 - Native SDK with RN 0.8.0 parity (v0.1.0)

**Goal:** A native Android app can integrate Notti from Maven Central and get the same behavior as `react-native-notti` 0.8.0 on Android, In-App Messages included (AD-007).
**Target:** Ported JUnit suite green in CI; example app passes the device checklist; `0.1.0` published to Maven Central.

### Features

**package-foundation** - DONE except the CI proof (2026-10-28: PR #1 merged with `--admin`, full gate green locally; open: T12 CI green/red proof, blocked by GitHub billing B-002)

- Gradle (KTS) project, version catalog, library module `:notti` (namespace `com.zeeplabs.notti`, AAR), example module placeholder
- `minSdk 24`, `compileSdk 36`, Java 17, Kotlin 2.0.x; consumer R8 rules; manifest skeleton
- Single version source (`NottiSdkVersion.CURRENT`) checked against the Gradle publication version
- Public API guard (Binary Compatibility Validator `.api` dump), Kotlin lint, CI on PRs, `maven-publish` + signing wired but not run

**core-port** - DONE except CI (318 tests green locally; PR #2 merged with `--admin` 2026-10-29, `b722874`; CI blocked by B-002)

- Port `NottiCore`, `NottiApiClient`, `NottiDeviceStore`, `NottiEventStore`, `NottiNetworkObserver`, parsing and the click relay from RN `v0.8.0`
- Remove the RN coupling (`NottiModule` companion statics, `ReactApplicationContext`, `WritableMap`) behind an owned runtime that stays cold-start safe
- `sdk_version` becomes a native constant; package `com.notti` becomes `com.zeeplabs.notti`
- Port the JUnit suite 1:1, traceable to the RN requirement IDs (SDK-, SDKCTR-, SDKOPEN-, SEGTEL-, DPF-)

**public-api** - Done (PR #4, `8abc0d9`, 2026-10-31; 444 tests)

- Kotlin facade: `Notti.initialize`, `login`/`logout`, `Notti.User` (tags, email, phone), subscription, location sharing, `Notti.Notifications` (requestPermission, listeners, initial click), device id accessor and observer (`trackEvent` moved to `in-app-messages`, as on iOS)
- `initialize` installs the process hooks when the host removed `NottiInitProvider`
- Java interop; listeners multi-subscriber with removable handles; callbacks on the main thread (iOS AD-009 parity)
- `NottiLogger` with level control (default error, no PII)

**push-integration** - DONE except CI (390 tests green locally; PR #3 merged with `--admin` 2026-10-29, `d5ab683`; Android counterpart of iOS `delegate-integration`)

- Manifest-merged `NottiFirebaseMessagingService` and `NottiInitProvider` process hook; activity-lifecycle click detection (`opened`/`clicked`), cold-start initial click
- `NottiBridge` manual forwarding for hosts with their own FCM service; documented manifest opt-out
- `POST_NOTIFICATIONS` flow on API 33+; a tapped push's `data` reaches the host untouched, the SDK never opens it

**in-app-messages** - DONE except CI and device checks (PR #5 merged with `--admin` 2026-10-31, `da644c3`; 718 tests)

- `Notti.trackEvent`, `Notti.InAppMessages.addClickListener` and the URL-open initialize option (moved from `public-api`)
- Port the RN In-App engine (session, coordinator, presenter, view factory, style, image loader, event store, accessibility)
- `push_permission` button; `url` actions opened in the browser with the iOS AD-020 allowlist and an option to turn it off (AD-006)
- Guard script: no URL open outside the one allowlisted file (AD-006 amended)

**example-and-release** - TASKS (2026-10-31, branch `feat/example-and-release`; spec approved, design + T1..T11 awaiting approval)

- Example app (one screen per public method), README (setup, FCM, opt-out, data collection, storage keys, Play Data safety, LGPD note, known limitations), CHANGELOG
- Device checklist (Android counterpart of iOS `DEVICE_CHECKLIST`, plus RN-era open items), image push rendered by the tray in background and killed states
- Release workflow on tag plus a local fallback runbook; signed dry run checked; Julio tags and publishes `0.1.0` (AD-015)

---

## M2 - RN SDK on top of the native SDK

**Goal:** `react-native-notti` uses this SDK as its Android engine. Only one Kotlin implementation exists.

### Features

**rn-consumes-native-sdk** - PLANNED (work happens mostly in `zeep-notti-react-native`)

- RN `android/build.gradle` depends on `com.zeeplabs:notti-android`; `NottiModule` maps JS calls and events onto the public API
- RN's Kotlin core, its manifest components and their tests are deleted
- On-device upgrade path verified (same `notti_prefs` keys, same device id)

---

## M3 - Beyond parity

**Goal:** Close known RN-era gaps, in lockstep with the iOS SDK.

### Features

**privacy-consent** - PLANNED: consent gating and local data wipe
**pii-secure-storage** - PLANNED: email/phone out of plain `SharedPreferences`, with migration
**notification-action-buttons** - PLANNED: RN `SDKACT-` spec
**queue-resilience** - PLANNED: per-event TTL, `Retry-After`, no head-of-line blocking

---

## Future Considerations

- Flutter wrapper consuming this SDK (`zeep-notti-flutter`)
- Compose-native In-App renderer
- Huawei HMS push
