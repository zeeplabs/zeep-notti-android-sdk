# Wrapper Support (0.2.0) Specification

## Problem Statement

M2 (`rn-consumes-native-sdk`, ROADMAP) makes `react-native-notti` depend on this artifact and delete its own
Kotlin core (RN repo, `.specs/features/android-native-sdk/`). The public API of 0.1.0 is enough for every JS
call and event except two things the backend needs from a wrapper:

1. **`sdk_version`.** `NottiCore.initialize` takes `sdkVersion`, but `NottiRuntime` always passes
   `NottiSdkVersion.CURRENT` and the public `Notti.initialize` has no parameter. An RN app on this SDK would
   report `0.2.0` instead of the RN package version (`0.9.0`), breaking every backend rule keyed on the RN
   version.
2. **`sdk_name`.** The backend gates the In-App `push_permission` button by a minimum version per SDK, keyed
   on a device field `sdk_name` (`react-native`, `ios`, `android`; `zeep-notti` AD-044). A device without it is
   judged as `react-native`. This SDK sends no `sdk_name`, so a native Android device is judged on the RN
   scale, and an RN app on top of this SDK must say `react-native`, not `android`.

The iOS SDK solved the same two gaps in its own `wrapper-support` (0.2.0) with an `@_spi(Wrapper)` entry
point. Kotlin has no SPI; Julio chose an opt-in annotation (2026-11-04, AD-022).

## Goals

- [ ] A wrapper sets the `sdk_version` and `sdk_name` the backend receives through an API that native hosts
      do not see unless they opt in.
- [ ] Native hosts report `sdk_name: "android"` and `sdk_version` = `NottiSdkVersion.CURRENT`.
- [x] 0.2.0 published on Maven Central (`br.com.zeeplabs:notti-android:0.2.0`).

## Out of Scope

| Feature | Reason |
| --- | --- |
| Any RN-side change | `zeep-notti-react-native`, feature `android-native-sdk` |
| Backend `sdk_name` column, validation and per-SDK gate | `zeep-notti` AD-044; this SDK only sends the field |
| New wrapper hooks beyond `sdk_version`/`sdk_name` | Only add what a wrapper proves it needs |
| PRR items or other behavior changes | Separate specs |

---

## User Stories

### P1: Wrapper reports its own identity ⭐ MVP

**User Story**: As the `react-native-notti` maintainer, I want to pass the RN package version and
`react-native` to the native SDK, so that the backend sees the same `sdk_version` an RN 0.8.0 app sent and
judges the device on the RN scale.

**Why P1**: Blocks RN `android-native-sdk` (its sdk_version/sdk_name requirement).

**Acceptance Criteria**:

1. WHEN a caller opted in to `@NottiWrapperApi` initializes through the wrapper entry point with
   `sdkName = "react-native"` and `sdkVersion = "0.9.0"` THEN the device profile SHALL send
   `sdk_name: "react-native"` and `sdk_version: "0.9.0"` (same PATCH, sync and ack rules as the other profile
   fields).
2. WHEN the wrapper entry point receives a blank `sdkVersion` or `sdkName` THEN the SDK SHALL omit that field,
   as `NottiCore` does today for a blank `sdkVersion`.
3. WHEN Kotlin code calls the wrapper entry point without opting in THEN compilation SHALL fail
   (`RequiresOptIn` level `ERROR`); Java callers are not checked by the compiler, and the KDoc SHALL say the
   API is for wrapper SDKs only and outside semver.
4. WHEN the wrapper entry point takes the same `Context`, app id, client key and `NottiOptions` as the public
   `initialize` THEN everything except `sdk_name`/`sdk_version` SHALL behave exactly as the public call
   (process-start hooks, cold-start click, queued identity calls, idempotence on equal config).
5. WHEN the wrapper entry point is called again with an equal config but a different `sdkName` or
   `sdkVersion` THEN the SDK SHALL treat it as a config change (rebuild, as for a different app id), and a test
   SHALL pin it.
6. WHEN `apiCheck` runs THEN the wrapper declarations SHALL appear in `notti/api/notti.api` (it is public
   bytecode); the README and the KDoc SHALL state they are not part of the semver promise.

**Independent Test**: runtime test with `MockWebServer`: the wrapper path sends the wrapper's values; the
public path sends `android` and `NottiSdkVersion.CURRENT`.

---

### P1: Native hosts send `sdk_name` ⭐ MVP

**User Story**: As the Notti backend, I want each native Android device to say it runs the Android SDK, so
that per-SDK minimum versions (AD-044) judge it correctly.

**Acceptance Criteria**:

1. WHEN a host calls the public `Notti.initialize` THEN the device profile SHALL send `sdk_name: "android"`
   with the `sdk_version` rules: sent when it differs from the last acked value, acked only when the backend
   echoes it, stored under a new key `notti_last_synced_sdk_name` (additive; every 0.1.0 and RN 0.8.0 key
   keeps its meaning).
2. WHEN the backend answers 2xx without echoing `sdk_name` (a backend before AD-044) THEN the SDK SHALL leave it
   unacked and send it again on the next registration; WHEN the backend rejects it (4xx) THEN only that field
   SHALL fail and the other profile fields SHALL still sync.
3. WHEN registration answers with a new device id THEN the `sdk_name` ack SHALL be cleared with the other
   profile acks.
4. WHEN a 0.1.0 install or an RN 0.8.x install upgrades THEN `notti_last_synced_sdk_name` is absent, so
   `sdk_name` SHALL be sent once.

**Independent Test**: core test with `MockWebServer`: PATCH body carries `sdk_name`; ack stored only on echo;
cleared on device id change.

---

### P1: Release 0.2.0 ⭐ MVP

**Acceptance Criteria**:

1. WHEN the release is cut THEN `NottiSdkVersion.CURRENT`, `CHANGELOG.md` and the README install line SHALL
   say `0.2.0` (`scripts/check-version.sh` green, tag mode).
2. WHEN the tag `0.2.0` is pushed THEN the release workflow SHALL upload it; approval and Portal Publish stay
   with Julio (AD-015).
3. WHEN 0.2.0 ships THEN the README data-collection and Play Data safety section SHALL list `sdk_name` with the
   other app-info fields (AD-011).

---

## Edge Cases

- WHEN the wrapper entry point is called before any other API call THEN cold-start behavior SHALL match the
  public `initialize`.
- WHEN the wrapper passes a value outside the backend enum THEN the backend answers 422 for that field; the SDK
  logs it by status code (no value in the log) and does not retry, like any profile field.
- WHEN the public `initialize` is called after the wrapper entry point with an otherwise equal config THEN it is
  a config change (identity goes back to `android`/`CURRENT`); a wrapper never mixes the two.
- No validation of length or characters on the client; the backend is the authority.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| WRP-01 | Wrapper identity: values reach the PATCH | Execute | Implemented |
| WRP-02 | Wrapper identity: blank omitted | Execute | Implemented |
| WRP-03 | Wrapper identity: opt-in required (Kotlin) | Execute | Implemented |
| WRP-04 | Wrapper identity: same behavior as public initialize | Execute | Implemented |
| WRP-05 | Wrapper identity: identity change rebuilds | Execute | Implemented |
| WRP-06 | Wrapper identity: API dump and docs | Execute | Implemented |
| WRP-07 | Native `sdk_name: "android"` with ack rules | Execute | Implemented |
| WRP-08 | `sdk_name` unacked/rejected handling | Execute | Implemented |
| WRP-09 | `sdk_name` ack cleared on device id change | Execute | Implemented |
| WRP-10 | Upgrade sends `sdk_name` once | Execute | Implemented |
| WRP-11 | Release 0.2.0: version, CHANGELOG, README | Execute | Implemented |
| WRP-12 | Release 0.2.0: tag and upload (Julio publishes) | Execute | Done (2026-11-04) |
| WRP-13 | Data safety lists `sdk_name` | Execute | Implemented |

**Coverage:** 13 total, all done (unit tests 725 -> 737); 0.2.0 published on Maven Central 2026-11-04.

## Success Criteria

- [ ] RN `android-native-sdk` builds against `br.com.zeeplabs:notti-android:0.2.0` and the backend sees
      `sdk_name: "react-native"` and the RN version from an RN app.
- [ ] A native app on 0.2.0 sends `sdk_name: "android"`.
