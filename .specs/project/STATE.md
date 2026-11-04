# State

**Last Updated:** 2026-11-01
**Current Work:** **0.2.0 released** on Maven Central (2026-11-04): `wrapper-support` (PR #18: `sdk_name`, opt-in `Notti.initializeForWrapper`, AD-022; CI caught a test race in `NottiUserFacadeTest`, fixed in the test), release PR #19, tag `0.2.0`, release run 41892221441, published by Julio in the Central Portal; GitHub Release `0.2.0`. Next: M2 in `zeep-notti-react-native` (feature `android-native-sdk`, RN AD-006): design, tasks, implementation against 0.2.0. Earlier: 0.1.0 released 2026-11-04 (PR #16, run 41833990890, M1 complete); `pre-release-review` (PR #12), `example-and-release` (PR #6), `in-app-messages` (PR #5), `public-api` (PR #4), `push-integration` (PR #3), `core-port` (PR #2), `package-foundation` (PR #1); group id change (PR #15, AD-020).

---

## Recent Decisions (Last 60 days)

### AD-001: This SDK is the single source of truth for Notti Android logic (2026-10-28)

**Decision:** The Kotlin core in `zeep-notti-react-native/android` is extracted into this repo. In M2, the RN SDK depends on this artifact and deletes its own Kotlin core. Mirrors `zeep-notti-ios-sdk` AD-001.
**Reason:** Removes a second Kotlin implementation; OneSignal model (`react-native-onesignal` wraps `OneSignal-Android-SDK`).
**Trade-off:** RN Android releases depend on this SDK's releases. Until M2 ships, both copies exist and fixes are applied twice.
**Impact:** The port preserves behavior, backend contract and `notti_prefs` keys. Any behavior change goes through a spec in this repo first.

### AD-002: M1 is a port of RN `v0.8.0`, not a rewrite (2026-10-28)

**Decision:** Baseline is `../zeep-notti-react-native/android` at tag `v0.8.0` (`291d5a7`, 2026-10-23). Until 0.1.0 ships, ported code stays behavior-identical: copy the RN file, then change only what the task lists (package, RN coupling, logger, visibility). Keep the single-thread executor, the blocking OkHttp client with retry/backoff and every `notti_prefs` key exactly as in RN. Port RN tests 1:1 (JUnit 4, Robolectric where RN uses it). No coroutines/Flow rewrite of ported code in M1.
**Reason:** Keeps the port verifiable against 536 existing tests and the RN requirement IDs; same rule as iOS AD-002/AD-017.
**Trade-off:** Known RN gaps (no consent API, PII in plain prefs, no `Retry-After` on the core client) ship in 0.1.0.
**Impact:** Feature specs reference RN requirement IDs instead of redefining behavior; each ported file starts with a provenance comment naming its RN source.

### AD-003: Package `com.zeeplabs.notti`, coordinates `com.zeeplabs:notti-android` (2026-10-28, Julio)

**Decision:** Kotlin package and Android namespace `com.zeeplabs.notti`; Maven groupId `com.zeeplabs` (superseded by AD-020: `br.com.zeeplabs`), artifactId `notti-android`. The `notti_prefs` file name and keys do not change.
**Reason:** RN 0.8.0 ships `com.notti.*`. In M2 (and in a brownfield app today) both are on the classpath; equal fully qualified names fail the build with duplicate classes (Android counterpart of iOS AD-021).
**Trade-off:** Ported files change their `package` line and imports; parity diffs must ignore that line.
**Impact:** `package-foundation`, every port task, README. Manifest authorities use `${applicationId}` plus a name distinct from RN's `nottiinitprovider` (see push-integration).

### AD-004: Distribution through Maven Central (2026-10-28, Julio)

**Decision:** Signed AAR + POM + sources + javadoc jars published through the Sonatype Central Portal. Publishing is an outward action done only in `example-and-release`, after explicit confirmation.
**Reason:** Default repository for Android libraries; RN and native hosts consume it with no extra repository line. GitHub Packages needs a token even for public packages; JitPack builds are unsigned.
**Trade-off:** Needs namespace verification of `com.zeeplabs` (DNS TXT on the domain that backs it) and a GPG signing key stored as CI secrets. Releases are immutable.
**Impact:** `package-foundation` wires `maven-publish` + `signing` with a local-repository dry run; `example-and-release` does the real publish. [Incerto] which domain verifies `com.zeeplabs`: to validate before the first publish (B-001).

### AD-005: Integration through manifest-merged components plus a manual API (2026-10-28; `ContentProvider` kept in push-integration design 2026-10-29; confirmed by Julio 2026-11-04)

**Decision:** Keep RN's model: the library manifest declares `NottiFirebaseMessagingService` (`MESSAGING_EVENT`) and `NottiInitProvider` (process-start hook, no I/O), so a host only calls `Notti.initialize`. Hosts with their own `FirebaseMessagingService` forward through `NottiBridge.onMessageReceived` / `onNewToken` and remove ours with `tools:node="remove"`.
**Reason:** One-line integration (iOS AD-004 counterpart). The `ContentProvider` is the only hook early enough for a cold-start click (RN P3-AC7). Android delivers FCM to one service per app, so the manual path is required.
**Trade-off:** A `ContentProvider` adds process-start cost and can be disabled by a host's manifest; `androidx.startup` would be the alternative. Settled in `push-integration` design: `ContentProvider` kept.
**Impact:** `push-integration` spec; README opt-out section.

### AD-006: In-App `url` actions are opened by the SDK, like iOS AD-020 (2026-10-28, Julio)

**Decision:** After the click listeners run and the message closes, the SDK opens an In-App `url` action in the browser (`ACTION_VIEW`, new task when not from an Activity) only for `http`/`https` (case-insensitive) with a non-empty host and no whitespace. Other schemes and `deeplink` actions are never opened and still reach the listener. On by default; an initialize option turns it off; every valid `initialize` sets it to the value it carries (iOS PRF-08 behavior). The URL is never logged.
**Reason:** Parity between the native SDKs that become RN's engine; matches OneSignal/Braze.
**Trade-off:** Diverges from RN 0.8.0 Android (never opens): the RN bridge must pass `false` to keep RN behavior until RN adopts the default. A listener that also opens the link opens it twice (README warns).
**Impact:** `in-app-messages` spec and its URL-open guard (two allowlisted files: push-permission settings and the URL opener).
**Amended (2026-10-31, Julio, in-app-messages spec):** the guard allowlists only the URL opener; the push-permission settings jump uses `Settings` intent actions, which the guard pattern does not match.

### AD-007: M1 / 0.1.0 = parity with RN 0.8.0, In-App included (2026-10-28, Julio)

**Decision:** 0.1.0 ships every Android behavior RN 0.8.0 has, In-App Messages included, plus the cross-SDK decisions of AD-006/AD-008. No other new behavior.
**Reason:** M2 swaps RN's engine; shipping without In-App would regress RN apps or block M2.
**Trade-off:** Larger first release (In-App is ~2.9k lines and ~5.6k test lines in RN).
**Impact:** ROADMAP M1 feature list.

### AD-008: Follow the iOS SDK where RN is silent (confirmed by Julio 2026-11-04; proposed 2026-10-28)

**Decision:** For behavior RN 0.8.0 does not define (it lives in the RN JS layer or did not exist), Android adopts the iOS SDK's decisions: multi-listener with removable subscriptions over the core's single handler, host callbacks on the main thread, `initialize` with the same normalized config is a no-op and a different one rebuilds the core, `baseUrl` defaults to `https://app.zeepnotti.app` (RN `SAAS_BASE_URL`), identity calls before `initialize` warn and are dropped. Each feature spec carries a parity table (RN 0.8.0 / iOS SDK / Android) and lists any divergence.
**Reason:** The two native SDKs become RN's engines; the RN JS facade must see one behavior.
**Trade-off:** New facade code outside the port rule; needs its own tests.
**Impact:** `public-api`, `push-integration`, `in-app-messages` specs.

### AD-009: Ported file formatting (confirmed by Julio 2026-11-04; proposed 2026-10-28)

**Decision:** Ported Kotlin files are re-indented from RN's 2 spaces to 4 (Kotlin official style) and checked with a whitespace-insensitive diff against RN (`diff -w`, ignoring the `package`/import lines). Lint findings RN code already has are suppressed per file (`@file:Suppress` or baseline), not fixed in M1.
**Reason:** Same approach as iOS AD-007: style rules for new code without breaking the token-level port.
**Trade-off:** Plain `diff` shows every line changed; reviewers use `diff -w`.
**Impact:** `core-port`, `in-app-messages` port tasks; lint tool choice in `package-foundation` design.

### AD-010: Public API guarded by Binary Compatibility Validator (confirmed by Julio 2026-11-04; proposed 2026-10-28)

**Decision:** The Kotlin Binary Compatibility Validator plugin dumps the public ABI to `notti/api/notti.api`; `apiCheck` runs in CI and every public change commits the updated dump. Ported internals are `internal`.
**Reason:** Counterpart of iOS `PUBLIC_SURFACE.txt` + `check-public-surface.sh`, with a maintained tool instead of a script.
**Trade-off:** One more Gradle plugin.
**Impact:** `package-foundation`, every feature touching public API.

### AD-011: Privacy, logging and Play Data safety (confirmed by Julio 2026-11-04, including the README Data safety answers; proposed 2026-10-28)

**Decision:** Logs go only through `NottiLogger` (default level error). No email, phone, push token, external user id, country, tag values or URLs in a log line; errors logged by class/code, not by message. README documents the Play Data safety answers for data the SDK collects (device ids, email, phone, user id, coarse country, app interactions, diagnostics). Test fixtures in new tests use synthetic values (`example.invalid`, `fixture`); ported tests keep RN's (iOS AD-008).
**Reason:** LGPD; iOS AD-005/AD-011/AD-014 counterparts. RN 0.8.0 calls `android.util.Log` directly in 19 places.
**Trade-off:** Rerouting those 19 calls is an allowed change in the port tasks.
**Impact:** `public-api` (logger), port tasks, `example-and-release` README. Data safety wording confirmed by Julio 2026-11-04.

### AD-012: Toolchain Gradle 9.4.1 + AGP 9.2.1 (built-in Kotlin) + Kotlin 2.2.21 (`apiVersion 2.0`), proved by spike S1 (2026-10-28, Julio; amended same day with S1 results)

**Decision:** Gradle 9.4.1, AGP 9.2.1 with built-in Kotlin, Kotlin compiler pinned to 2.2.21 through the `buildscript` classpath (AGP 9.2.1 alone brings KGP 2.2.10), `languageVersion = apiVersion = 2.0`, JVM 17. Spotless 8.10.4 + ktlint 1.8.0 (`ktlint_official`). vanniktech `maven.publish` 0.37.0 (`AndroidSingleVariantLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources(), variant = "release")`; the Boolean constructor is deprecated). ABI guard: standalone BCV 0.18.2 wired by hand (`KotlinApiBuildTask` `apiBuild` on `compileReleaseKotlin` output, `Copy` `apiDump` to `notti/api/notti.api`, `KotlinApiCompareTask` `apiCheck` hooked into `check`). Android Lint keeps `warningsAsErrors` but disables `GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`. Starting version `0.1.0`, MIT.
**Reason (S1, scratchpad `spike-s1`, all checks 1-7 passed):** build + 3 smoke tests green on JDK 17 with Robolectric 4.14.1; local publish produces `.aar/.pom/-sources/-javadoc/.module` with no signing key; the AAR has `proguard.txt` and the manifest; the RN 0.8.0 example (RN 0.87.1, which itself runs AGP 9.2.1 / KGP 2.2.10, not the 2.1.20 its `ext` declares) builds with the dependency and calls a public probe from Kotlin; a plain AGP 8.13.2 + KGP 2.1.20 consumer also compiles and calls it (class metadata `mv=[2,0,0]`, class file 61). KGP's own ABI validation does not work under built-in Kotlin (`abiValidation` extension missing; on 2.3/2.4 no task is created; the opt-out path dumps an empty file and passes), and BCV's auto-wiring also expects `kotlin-android`, hence the manual wiring, proved by a public class failing `apiCheck`. The disabled lint checks fired 19 errors, all "newer version available", none on code.
**Trade-off:** the ABI wiring uses BCV task classes that may change between versions (pinned; re-check on upgrade; move to KGP ABI validation once it supports built-in Kotlin). The POM declares `kotlin-stdlib` 2.2.21 at compile scope, which upgrades a consumer's stdlib (worked with KGP 2.1.20); `kotlin.stdlib.default.dependency=false` with an older stdlib is a todo. OkHttp 5.0-5.4 forced by a host compiles; 5.5.1 needs compileSdk 37 (not installed); binary compatibility is not proven.
**Impact:** `gradle/libs.versions.toml`, `notti/build.gradle.kts`, CI, AGENTS.md, `.specs/codebase/TESTING.md`. Fallback (AGP 8.13 + KGP 2.0.21) not needed.

### AD-013: Runtime dependency versions = what RN 0.8.0 resolves (2026-10-28, Julio)

**Decision:** Pin runtime dependencies to the versions the RN 0.8.0 example resolves today (S1 check 7): `firebase-messaging` 25.1.2, `lifecycle-process` 2.6.2, `core-ktx` 1.13.1, `activity` 1.7.0, `annotation` 1.9.1; OkHttp 4.12.0 (RN resolves 4.9.2 at runtime but its tests run 4.12.0 MockWebServer). Raise one only when ported code needs it, with a note.
**Reason:** These are the versions the RN code is proven on (AD-002), and they act as low floors, so the SDK does not force upgrades on hosts. S1 used latest-ish versions (25.1.3, 2.10.0, 1.17.0, 1.12.4) and they won Gradle resolution in the RN example, upgrading the app.
**Trade-off:** Older floors; a host on newer versions still resolves its own (Gradle picks the highest).
**Impact:** `package-foundation` T3/T4 catalog; T3 must build with these (fall back to the lowest version that compiles with compileSdk 36 and record it).

### AD-014: Facade re-initializes the one core in place (2026-10-30, public-api design D1)

**Decision:** `NottiRuntime` keeps one core per process (`ensureCore()`, RN's lazy core); `Notti.initialize` calls `core.initialize`, which no-ops on an identical config and re-registers on a different one (RN SDK-07, L2). The iOS SDK rebuilds its core instead.
**Reason:** Same observable result as iOS (0 / 1 extra registration) with the RN code already proven; no executor hand-off or in-flight mutation questions.
**Trade-off:** Internal difference between the native SDKs; specs compare outcomes, not mechanisms.
**Impact:** `NottiRuntime.start` built on `ensureCore`; `NottiRuntimeTest` replace-core case rewritten.

### AD-015: Release 0.1.0 through a tag workflow with a local fallback; Julio tags and publishes (2026-10-31, Julio, example-and-release spec)

**Decision:** `.github/workflows/release.yml` runs on a `X.Y.Z` tag: version check, full gate, then a signed upload to the Central Portal without automatic release. `docs/RELEASING.md` documents the same upload from Julio's machine while B-002 blocks CI. In both paths Julio checks the deployment in the Portal and presses Publish. The agent prepares the release PR, CHANGELOG, runbook and device checklist; Julio creates and pushes the tag, runs or watches the upload and publishes.
**Reason:** Central releases are immutable, so the Portal review is the last gate; CI may stay blocked; same split as iOS (tag and `pod trunk push` by Julio).
**Trade-off:** Two upload paths to keep equivalent; the local path depends on Julio's machine having the key and token.
**Impact:** Refines AD-004 ("publishing done only in example-and-release after explicit confirmation"): the agent never runs the upload. `example-and-release` EXR-14..EXR-19.

### AD-016: Example config from untracked files; Google Services plugin only when its file exists (2026-10-31, Julio, example-and-release spec)

**Decision:** `example/notti.properties` (git-ignored; tracked `.sample` with placeholders) feeds `BuildConfig` with the app id, client key and optional base URL. `:example` and `:example-manual-fcm` apply the Google Services plugin only when the module has a `google-services.json`. Without them the apps build and show "not configured" without calling `Notti.initialize`.
**Reason:** No secret in git; CI builds with nothing; same Firebase path the README teaches integrators.
**Trade-off:** A conditional plugin in the example build scripts.
**Impact:** `example-and-release` EXR-01, EXR-04..EXR-06; `.gitignore`; a tracked-secrets CI check.

### AD-017: Host floors are Kotlin 2.0 and `compileSdk` 34 (2026-11-01, Julio, pre-release review F1/F3)

**Decision:** The library sets `kotlin.stdlib.default.dependency=false` and depends on `kotlin-stdlib` 2.0.21, so the POM no longer carries the 2.2.21 stdlib; class metadata stays at 2.0 (`languageVersion`/`apiVersion` 2.0, AD-012). The AAR declares `aarMetadata.minCompileSdk = 34`, the floor `core-ktx` 1.13.1 already imposes, instead of AGP's default (the library's `compileSdk` 36). CI fails if either drifts.
**Reason:** AD-013 says the SDK must not force upgrades on hosts. A 2.2.x stdlib in the POM breaks hosts compiling with Kotlin 2.0 (metadata one minor ahead at most), which includes RN hosts on Kotlin 2.0.21 for M2. Both are free to relax later but cheap to fix before the first immutable release.
**Trade-off:** The library compiles against an older stdlib than its compiler (allowed with `apiVersion` 2.0). A host on Kotlin 1.9 is still not supported.
**Impact:** Supersedes the AD-012 stdlib trade-off and its todo. `notti/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, CI "AAR and POM contents", README Requirements.

### AD-018: Public API evolution rule and Java `null` tolerance (2026-11-01, Julio, pre-release review F4/F5)

**Decision:** After 0.1.0, a new `NottiOptions` field is added as a new defaulted constructor parameter while the previous constructor stays as a `@Deprecated(level = HIDDEN)` overload, so code compiled against the older version (Kotlin callers bind the synthetic defaults constructor) keeps linking; `apiCheck` must show only additions. Public enums (`NottiInAppAction.Type`, `NottiLogLevel`) may gain values in a minor release; docs show `when` with an `else` branch. `Notti` facade methods that take `String`, `Map` or `List` accept Java `null` (Kotlin type `String?` etc. where the JVM signature is unchanged): the call is logged by method name and ignored; `null` entries are dropped. Listener and callback parameters stay non-null.
**Reason:** The README promises "No SDK call throws", and a library compiled against 0.1.0 (RN M2) must survive a 0.2.0 SDK on the host.
**Trade-off:** Nullable parameter types in the Kotlin signatures look looser than they are.
**Impact:** `Notti.kt`, `NottiOptions.kt` KDoc, README In-App and Java sections.

### AD-019: Three lifecycle fixes to ported behavior before 0.1.0 (2026-11-01, Julio, pre-release review F6/F7/F10)

**Decision:** Exceptions to AD-002, approved before the release: (1) the SDK's `NottiPermissionActivity` is invisible to the runtime's current-Activity tracking, to the In-App tracker and to tap detection; (2) the permission dialog is shown only while the process is at least `STARTED`, otherwise the request answers `false` without setting `notti_permission_requested`; (3) tap detection catches any exception from reading the Intent's extras.
**Reason:** (1) the Activity is new in this SDK (RN used the host's `PermissionAwareActivity`), so RN parity does not cover it; (2) and (10) are RN bugs that leave a user marked "asked" with no dialog, or crash the host on a crafted Intent.
**Trade-off:** Behavior differs from `react-native-notti` 0.8.0 until M2 moves RN onto this SDK.
**Impact:** `NottiActivityLifecycleListener`, `NottiInAppPresenter` (tracker), `NottiPermissionRequester`, their tests; `docs/DEVICE_CHECKLIST.md`.

### AD-020: Maven group id `br.com.zeeplabs` (2026-11-03, Julio)

**Decision:** Coordinates are `br.com.zeeplabs:notti-android`. Supersedes the group id part of AD-003; the Kotlin package and Android namespace stay `com.zeeplabs.notti`, and the `notti_prefs` file and keys do not change.
**Reason:** Central verifies a namespace by DNS on its reversed domain. ZeepLabs owns `zeeplabs.com.br`, not `zeeplabs.com`; `br.com.zeeplabs` was verified on 2026-11-03 (B-001).
**Trade-off:** Group id and Kotlin package differ. Central checks only the group id, and nothing was published, so no host breaks.
**Impact:** `notti/build.gradle.kts` (`group`, `coordinates`), the example apps, CI artifact path and POM check, `scripts/check-release-artifacts.sh`, README, CHANGELOG, `docs/RELEASING.md`, `AGENTS.md`. RN M2 depends on `br.com.zeeplabs:notti-android`.

### AD-021: Device checklist is a guide, not a recorded release gate (2026-11-04, Julio)

**Decision:** `docs/DEVICE_CHECKLIST.md` stays as a testing guide. Recording Pass/Fail per item is optional and no longer gates a release; Julio tests on a device and states it in the release PR. Supersedes EXR-12/EXR-13 as gates. 0.1.0: Julio reported a full device test on Android (2026-11-04).
**Reason:** Julio's call: recording 27 items per release costs more than it gives for a one-maintainer SDK.
**Trade-off:** No written per-item evidence of what passed on which device and API level; a regression found later cannot be traced to a recorded run.
**Impact:** `docs/DEVICE_CHECKLIST.md`, `docs/RELEASING.md` (Before the tag, After the release), README (Device proof limitation removed), CHANGELOG known limitations, ROADMAP.

### AD-022: Wrapper identity through an opt-in API (2026-11-04, Julio)

**Decision:** Wrapper SDKs (`react-native-notti`) set `sdk_name` and `sdk_version` through a public entry point marked with a `@RequiresOptIn(level = ERROR)` annotation `@NottiWrapperApi`. Native hosts send `sdk_name: "android"` and `NottiSdkVersion.CURRENT`. Feature `wrapper-support` (0.2.0).
**Reason:** Kotlin has no SPI; this is the closest to the iOS SDK's `@_spi(Wrapper)`. A plain `NottiOptions` field would let any app overwrite its identity and become a stable contract.
**Trade-off:** The declarations are public bytecode and appear in `notti.api`; Java callers are not stopped by the compiler. KDoc and README say the API is for wrappers only and outside semver.
**Impact:** `Notti` (wrapper entry point), `NottiRuntime`, `NottiCore` (`sdk_name` profile field, `notti_last_synced_sdk_name`), README data collection, RN `android-native-sdk`.

---

## Active Blockers

None.

---

## Resolved Blockers

### B-001: Maven Central namespace for `com.zeeplabs`

**Discovered:** 2026-10-28
**Impact:** Blocks only the 0.1.0 publish (`example-and-release`), not development.
**Workaround:** Local Maven repository / `mavenLocal()` for the example app and RN M2 experiments.
**Resolution:** The domain is `zeeplabs.com.br`, not `zeeplabs.com`. Namespace `br.com.zeeplabs` verified in the Central Portal by DNS TXT (Julio, 2026-11-03); group id changed (AD-020). Signing key is a separate setup step in `docs/RELEASING.md`.

### B-002: GitHub Actions did not start jobs (billing)

**Discovered:** 2026-10-28, PR #1 run 40792894429
**Resolved:** 2026-10-31. CI ran on PR #6: the first run went red on a racy test (`NottiRuntimeTest.registrationSyncSendsTheSdkVersionConstant`, fixed in `191b8df`), the second went green (run 41226597871).
**Follow-up:** `package-foundation` T12 closed the same day: red proofs for lint, ABI, version drift and `com.notti` on throwaway PRs #7..#10, closed unmerged.
**Note:** PRs #1..#5 were merged with `--admin` without CI on Julio's decision, after the full gate passed locally.

---

## Lessons Learned

Carried from `zeep-notti-ios-sdk` (`.specs/LESSONS.md`, candidates there), to apply here from the start:

- An architectural invariant needs a CI check that fails on the forbidden edge (e.g. no `com.notti` package, no URL open outside the allowlist); build success does not prove it.
- Guard scripts fail on missing inputs, distinguish grep exit 1 from 2, and run on the CI's own OS.
- Persistence identifiers used only by production wiring (the `notti_prefs` file name) need their own golden test; per-test stores never exercise them.

From this repo:

- Never run `spotlessApply` while a ported file is uncommitted and has no `@file:Suppress` block yet: it reformats RN code and breaks the port diff. Add the suppressions first (or commit), then apply; check which files it touched (in-app-messages T6, 2026-10-31).
- A ported file's dependencies decide the task order, not the file size: map each file's references before splitting port tasks (`NottiInAppAccessibility` needed the view factory, in-app-messages T2).

---

## Quick Tasks Completed

| # | Description | Date | Commit | Status |
| --- | --- | --- | --- | --- |

---

## Deferred Ideas

- Detect a warm tap in `singleTop`/`singleTask` hosts without `setIntent` through `ComponentActivity.addOnNewIntentListener` (RN 0.8.0 requires `setIntent`; post-M1, both SDKs' docs)

- Foreground image push: FCM does not display a notification while the app is in the foreground and RN 0.8.0 only emits `received`; showing it (with the image) would be new behavior, after M1

- Coroutines/Flow rewrite of the core (after M1, with iOS's Swift concurrency equivalent)
- `androidx.startup` instead of the `ContentProvider` (decide in push-integration)

---

## Todos

- [Provável] Inherited RN 0.8.0 bug: `NottiActivityLifecycleListener.onActivityCreated` skips a restored (`savedInstanceState != null`) or Recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) launch intent but does not mark it handled, so the `onActivityResumed` right after reports a phantom `opened` + `clicked`. RN tests call only `onActivityCreated`. Recents half fixed here by public-api T3 (resumed path skips the history flag; created+resumed test `a Recents reopen seen by onActivityCreated then onActivityResumed emits nothing`). Open: the restore half here (not detectable on resume: mark the intent handled in `onActivityCreated`?) and both halves in RN (found 2026-10-30, public-api design D2)

- [x] Flaky `NottiCoreTest` logout family: root cause found and fixed in the test fake (2026-11-02, branch `fix/logout-flaky-test`). `FakePrefsForCore` was a plain `LinkedHashMap`; `NottiCore.setContactField` writes the held email/phone on the caller's thread (e.g. from `logout()`) while the executor reads `device_id`/token, so a read mid-resize could return `null`, queue the mutation as "not registered" and drop a request (expected 5, saw 4; no `external_user_id` PATCH). Evidence: an overlap probe on the fake saw concurrent access in 9 of 128 tests, the known flaky ones included; baseline 1/60 runs failed (another probe-flagged test), with the fake synchronized 0/60. Production is not affected: the real `SharedPreferences` is thread-safe. Ported to `../zeep-notti-react-native` (PR #45, `5b25d33`, 2026-11-03)

- Cross-SDK (RN, iOS N5, here): In-App image loader caps bytes (5 MB) but not decoded pixel dimensions; a small file can still decode to a huge bitmap. Out of scope of `in-app-messages` (ported as RN, Julio 2026-10-31); needs its own spec in all SDKs.
- Port PRR-07 (foreground check before the permission dialog) and PRR-10 (guarded tap detection) to `react-native-notti`, or let M2 replace its core (AD-019)
- Move the ABI guard to KGP ABI validation when it supports AGP built-in Kotlin

---

## Preferences

- Specs and code comments in English (same as the iOS repo); conversation in pt-BR.
