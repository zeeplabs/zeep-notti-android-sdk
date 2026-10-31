# Example App and Release 0.1.0 Specification

## Problem Statement

All M1 SDK code is merged (`package-foundation`, `core-port`, `push-integration`, `public-api`, `in-app-messages`; 718 tests), but nothing proves it works on a device, and an integrator has only draft docs. The example app shows three controls, the README sections are marked draft, the CHANGELOG has no release section, and Maven Central publishing is wired but never run. Many behaviors are unit-tested only: the FCM token flow, the `POST_NOTIFICATIONS` dialog, cold-start clicks, the tray rendering of an image push, the four In-App layouts, URL opening and the RN 0.8.0 upgrade path. This feature makes the example exercise every public method, finishes the integrator docs, writes the device checklist and the release runbook, and stages `0.1.0` so Julio can tag and publish it. Android counterpart of the iOS `example-and-release`.

## Goals

- [ ] The example app calls every public method of `Notti`, `Notti.User`, `Notti.Notifications` and `Notti.InAppMessages`; `example-manual-fcm` covers `NottiBridge`. A script fails CI when a public method is not referenced.
- [ ] The example builds with placeholder config on CI and runs on a device with Julio's real app id, client key and `google-services.json`, all untracked.
- [ ] A README an integrator follows without reading the specs: install, Firebase, initialize, permission, identity, listeners, In-App, own FCM service, opt-outs, data collected, storage keys, Play Data safety, LGPD note, known limitations.
- [ ] `docs/DEVICE_CHECKLIST.md` lists everything CI cannot prove, with steps and expected results.
- [ ] `0.1.0` staged: CHANGELOG section, release workflow on tag, local fallback runbook, signed dry run checked. No tag, push of a tag or Maven Central publish made by the agent.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Creating or pushing the `0.1.0` tag, the GitHub Release, the Maven Central upload and the Portal "Publish" | Julio does them after the device checklist (decided 2026-10-31, AD-015) |
| Running the device checklist | Needs a physical device, a real push and Julio's credentials. The agent prepares it, Julio runs it |
| Namespace DNS record, GPG key creation, repository secrets | Julio's accounts (B-001); the runbook gives the exact steps |
| Publishing the example to Google Play | Developer tool, not a product |
| Changing SDK behavior | Findings become todos or a follow-up spec (AD-001). Only a blocking bug fix gets its own commit with a test |
| Committing an app id, client key, `google-services.json`, push token or signing material | LGPD and secret rules |
| Jetpack Compose in the example | Adds dependencies the SDK does not need; plain Views keep the example close to any host |
| RN bridge | M2 |
| Fixing B-002 (CI billing) | Organization billing; the release has a local fallback (AD-015) |

---

## Assumptions & Decisions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Release mechanism | `.github/workflows/release.yml` runs on a `X.Y.Z` tag: full gate, then a signed upload to the Central Portal without automatic release. `docs/RELEASING.md` documents the same upload from Julio's machine as fallback while B-002 blocks CI. In both paths Julio checks the deployment in the Portal and presses Publish | Releases are immutable; a manual Portal step is the last review. CI may stay blocked | y (Julio, 2026-10-31) |
| Who releases | The agent prepares the PR, CHANGELOG, runbook and checklist. Julio creates and pushes the tag, runs or watches the upload and publishes | Outward-facing and irreversible (AD-004), same split as iOS | y (Julio, 2026-10-31) |
| Example config | Untracked `example/notti.properties` (`notti.appId`, `notti.clientKey`, optional `notti.baseUrl`) becomes `BuildConfig` fields. The Google Services plugin is applied to `:example` and `:example-manual-fcm` only when that module has a `google-services.json`. Without them the app builds with placeholders and shows "not configured" | No secret in git; the same Firebase path the README teaches. Library never applies the plugin (AD-013) | y (Julio, 2026-10-31) |
| Example UI | Plain Views in Kotlin, one section per API group, a scrolling event log | No Compose or AppCompat dependency | [Provável], settle in Design |
| Data safety wording | README states what the SDK collects and the answers it implies; Julio (and legal, when needed) confirms before publish | AD-011 todo | Open: gates the publish, not the PR |
| Version | `NottiSdkVersion.CURRENT` is already `0.1.0` | `check-version.sh` guards drift | y (code) |
| Plugin task names | vanniktech 0.37.0 `publishToMavenCentral` (upload only) with `mavenCentralUsername`/`mavenCentralPassword`/`signingInMemoryKey*` properties | Verify against the plugin docs in Design | [Provável] |

---

## User Stories

### P1: Example app exercises the whole public API ⭐ MVP

**User Story**: As the SDK maintainer, I want an app that calls every public method so that I can see the SDK work on a device and catch integration bugs unit tests cannot.

**Why P1**: The only way to close the device-only todos of every M1 feature.

**Acceptance Criteria**:

1. EXR-01: WHEN `:example` and `:example-manual-fcm` build without `notti.properties` and without `google-services.json` THEN debug and minified release SHALL build, from the project and from the published AAR (`-Pnotti.usePublished=true`), as CI does today.
2. EXR-02: WHEN the example runs THEN every public function and property of `Notti`, `Notti.User`, `Notti.Notifications` and `Notti.InAppMessages` in `notti/api/notti.api` SHALL be reachable from a control or shown on screen, and `NottiBridge` SHALL be called by `:example-manual-fcm`. `scripts/check-example-coverage.sh` SHALL fail when one of them is not referenced in the example sources, with a self-test.
3. EXR-03: WHEN the app runs THEN it SHALL show the device id and its changes, the permission result, received and clicked notifications (data keys only), In-App clicks (message id, button id, action type only), the initial click, and a log level control. It SHALL NOT display or log the client key, push token, data values or URLs, and SHALL NOT log email, phone or user id values typed into its fields.
4. EXR-04: WHEN `notti.properties` is absent or incomplete THEN the app SHALL NOT call `Notti.initialize`, SHALL show a visible "not configured" state, and the other controls SHALL still run (the SDK warns and drops them).
5. EXR-05: WHEN a module has `google-services.json` THEN the build SHALL apply the Google Services plugin to that module only; WHEN it has none THEN the plugin SHALL NOT be applied and the build SHALL pass. The library module SHALL never apply it.
6. EXR-06: WHEN the files are checked THEN `example/notti.properties` and every `google-services.json` SHALL be git-ignored, a tracked `example/notti.properties.sample` SHALL hold placeholders only (`fixture`, `example.invalid`), and a CI check SHALL fail if a `google-services.json` or `notti.properties` is tracked.

**Independent Test**: build both example apps on CI; on a device with the local files, tap every control and see the matching request in the Notti dashboard.

---

### P1: README for integrators ⭐ MVP

**User Story**: As an Android developer evaluating Notti, I want a README that takes me from zero to a working push so that I do not read the specs.

**Why P1**: Draft sections from three features need one coherent document before any release.

**Acceptance Criteria**:

1. EXR-07: WHEN a developer follows the README THEN it SHALL cover, in order: requirements (minSdk 24, compileSdk, a Firebase project), install (`mavenCentral()`, `com.zeeplabs:notti-android`), Firebase setup (`google-services.json` and the plugin in the app), `Notti.initialize` and `NottiOptions`, notification permission (Android 13+ and older), identity and tags, listeners and the initial click, In-App messages and URL opening, logging, notification icon and color meta-data, an app with its own `FirebaseMessagingService`, removing the process-start hook and what is lost, `singleTop`/`singleTask` and `setIntent`, R8 (no rules needed), Java usage. No section SHALL stay marked draft.
2. EXR-08: WHEN a developer reads the data section THEN it SHALL list every field sent to the backend, the `notti_prefs` file and its keys, the Play Data safety answers the SDK implies (data types, purpose, encrypted in transit, shared or not, optional or required), and an LGPD note that recommends the app owner's own legal review and never claims compliance.
3. EXR-09: WHEN a developer reads known limitations THEN it SHALL list at least: no consent API, email and phone in plain `SharedPreferences`, no `Retry-After` on the core client, no notification shown while the app is in the foreground, `setIntent` needed for warm taps in `singleTop`/`singleTask`, effects of removing the provider, a link opened twice when a listener also opens it, no pixel-size cap on In-App images, `com.notti` coexistence with `react-native-notti`, `kotlin-stdlib` 2.2.21 in the POM, and every checklist item not yet passed.
4. EXR-10: WHEN the README shows a Notti host, app id or key THEN it SHALL use placeholders, and the default host SHALL be stated once.
5. EXR-11: WHEN the README documents events and logout THEN it SHALL state that a tap reports `opened` and `clicked` (SDKOPEN-11) and that `logout` clears the external user id on the server and email and phone locally (DLOG-22).

**Independent Test**: a reader with a new Android project reaches a registered device using only the README.

---

### P1: Device validation checklist ⭐ MVP

**User Story**: As Julio, I want one checklist of everything CI cannot prove so that I validate `0.1.0` on devices in one sitting.

**Why P1**: The publish gate; Maven Central releases are immutable.

**Acceptance Criteria**:

1. EXR-12: WHEN the checklist is read THEN `docs/DEVICE_CHECKLIST.md` SHALL list each unproven item with device/API level, prerequisites, steps, the expected observable result and a pass/fail box. It SHALL include at least: FCM token and device registration; permission on API 33+ (grant, deny, deny twice then the settings path) and on API < 33; location sharing; subscription off and on; login, logout, tags, email, phone; push received in foreground (`received` only), background and killed; a tap in each state (warm, cold start with the provider, cold start with the provider removed and `initialize` in `Application.onCreate`); `singleTop` with `setIntent`; reopening from Recents reports nothing; an image push rendered by the tray in background and killed states; `:example-manual-fcm` receiving and forwarding; the four In-App layouts, swipe and close; a `url` action opening the browser, a `deeplink` not opened, `opensInAppUrls = false`; the `push_permission` button (prompt and settings); events queued offline and sent on reconnect; the minified release build on a device; TalkBack on an In-App message; the upgrade from the RN 0.8.0 example to a native build with the same `applicationId` keeping the device id (core-port).
2. EXR-13: WHEN an item comes from a `STATE.md` todo or a feature traceability gap THEN it SHALL cite it, and closing it in `STATE.md` SHALL be a post-checklist step.

**Independent Test**: Julio runs it top to bottom on one API 33+ device and one API < 33 device (or emulator for the items that allow it, marked as such).

---

### P2: Release preparation

**User Story**: As Julio, I want `0.1.0` staged so that tagging and publishing are a few safe, documented steps.

**Why P2**: Needed for the release, but the example, README and checklist come first.

**Acceptance Criteria**:

1. EXR-14: WHEN the release PR merges THEN `CHANGELOG.md` SHALL have a `## [0.1.0] - TBD` section (Added, Fixed, Known limitations, Migration from `react-native-notti` 0.8.0: same `notti_prefs` keys and device id, `com.zeeplabs.notti` package, In-App URL default differs) and the README status banner SHALL describe `0.1.0` instead of pre-release. Julio sets the date on release day.
2. EXR-15: WHEN `scripts/check-version.sh` runs THEN `NottiSdkVersion.CURRENT` and the top CHANGELOG section SHALL both be `0.1.0`; on a tag build it SHALL also require the date, the tag and the banner (existing checks).
3. EXR-16: WHEN a `X.Y.Z` tag is pushed THEN `release.yml` SHALL run the version check and the full build gate, then upload the signed publication to the Central Portal without releasing it, using repository secrets for the Portal token and the signing key. It SHALL NOT run on pull requests or branches, SHALL pin actions by SHA, and SHALL never print a secret.
4. EXR-17: WHEN the signing check runs THEN a publish to the local build repository signed with a throwaway key generated for the check SHALL produce a `.asc` file for the AAR, POM, sources jar, javadoc jar and Gradle module, and the POM SHALL carry the fields Central requires (name, description, url, license, developer, scm). `scripts/check-release-artifacts.sh` does it locally and in CI.
5. EXR-18: WHEN Julio releases THEN `docs/RELEASING.md` SHALL give the exact steps: namespace verification for `com.zeeplabs` (B-001), GPG key creation and keyserver upload, the four secrets, the local fallback with `~/.gradle/gradle.properties`, checking the deployment in the Portal, Publish, the GitHub Release, and the post-release updates (`STATE.md`, next `[Unreleased]`).
6. EXR-19: WHEN the release PR is opened THEN its description SHALL list the pre-publish gates: device checklist passed, Data safety wording confirmed (AD-011), B-001 resolved, release path chosen (CI or local fallback while B-002 is open), proposed ADs confirmed (AD-005, AD-008..AD-011).

---

### P2: CI runs the new checks

**Acceptance Criteria**:

1. EXR-20: WHEN CI runs on a PR THEN the `checks` job SHALL run `check-example-coverage.sh`, the tracked-secrets check and their self-tests, and the `build` job SHALL run `check-release-artifacts.sh` after the existing publish step.

---

## Edge Cases

- WHEN a maintainer builds without any local file THEN the example compiles and shows "not configured" (EXR-04); it never crashes on a missing property.
- WHEN `notti.properties` has an app id but no client key THEN it counts as not configured; the app does not pass a half config to `initialize`.
- WHEN `google-services.json` exists but its `package_name` differs from the module's `applicationId` THEN the build fails with the plugin's own error; the README says to register both example package names in Firebase.
- WHEN a public method is added to the SDK later THEN `check-example-coverage.sh` fails until the example calls it.
- WHEN the release workflow runs on a tag that does not match `NottiSdkVersion.CURRENT` THEN `check-version.sh` fails before any upload.
- WHEN a secret is missing in the release workflow THEN the job fails before upload with a message naming the missing secret, not its value.
- WHEN the upload succeeds but validation fails in the Portal THEN nothing is released; the runbook says how to drop the deployment and retry with a new build of the same version.

---

## Parity with the iOS SDK

| Item | iOS `example-and-release` | Android |
| --- | --- | --- |
| Example covers the public API, drift script | EXR-02 (`PUBLIC_SURFACE.txt`) | EXR-02 (`notti.api`) |
| Local untracked config, "not configured" state | xcconfig | `notti.properties` + `google-services.json` |
| Second integration mode | swizzling off, manual forwarding | `:example-manual-fcm` (existing) |
| Extension target | NSE | none: the tray renders FCM images |
| README snippet check | EXR-13 (P1) | Not in scope: snippets are short and the example coverage check covers the API; reconsider if the README drifts |
| Release | tag + `pod trunk push` by Julio | tag + Central Portal publish by Julio; workflow plus local fallback |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| EXR-01 | Example app | T1-T11 | Done (see traceability.md) |
| EXR-02 | Example app | T1-T11 | Done (see traceability.md) |
| EXR-03 | Example app | T1-T11 | Done (see traceability.md) |
| EXR-04 | Example app | T1-T11 | Done (see traceability.md) |
| EXR-05 | Example app | T1-T11 | Done (see traceability.md) |
| EXR-06 | Example app | T1-T11 | Done (see traceability.md) |
| EXR-07 | README | T1-T11 | Done (see traceability.md) |
| EXR-08 | README | T1-T11 | Done (see traceability.md) |
| EXR-09 | README | T1-T11 | Done (see traceability.md) |
| EXR-10 | README | T1-T11 | Done (see traceability.md) |
| EXR-11 | README | T1-T11 | Done (see traceability.md) |
| EXR-12 | Device checklist | T1-T11 | Done (see traceability.md) |
| EXR-13 | Device checklist | T1-T11 | Done (see traceability.md) |
| EXR-14 | Release preparation | T1-T11 | Done (see traceability.md) |
| EXR-15 | Release preparation | T1-T11 | Done (see traceability.md) |
| EXR-16 | Release preparation | T1-T11 | Done (see traceability.md) |
| EXR-17 | Release preparation | T1-T11 | Done (see traceability.md) |
| EXR-18 | Release preparation | T1-T11 | Done (see traceability.md) |
| EXR-19 | Release preparation | T1-T11 | Done (see traceability.md) |
| EXR-20 | CI | T1-T11 | Done (see traceability.md) |

**Coverage:** 20 total, 20 mapped to tasks (see `tasks.md`).

Carried in from earlier features: SDKOPEN-11 and DLOG-22 docs (core-port), PUSH-08 final wording and the device checks (push-integration), `google-services.json` guidance and the final README (public-api), the In-App device checks (in-app-messages), the upgrade-path check (core-port SDK device id story).

---

## Success Criteria

- [ ] CI (or the local full gate while B-002 is open) builds both example apps with placeholder config, and the new checks pass.
- [ ] Julio can run the checklist with only `notti.properties` and `google-services.json` to add.
- [ ] README plus CHANGELOG are enough for a new integrator; no spec is referenced for setup.
- [ ] Nothing tagged, uploaded to Maven Central or published by the agent.
