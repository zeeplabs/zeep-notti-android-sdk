# zeep-notti-android-sdk

**Vision:** Native Android SDK (Kotlin) for Notti push notifications. It is the single source of truth for Notti's Android logic, used directly by native apps and, later, by `zeep-notti-react-native` (and Flutter) as their Android engine.
**For:** Android developers who integrate Notti into native apps (Views or Jetpack Compose), and the Notti cross-platform SDKs that wrap it.
**Solves:** Today the Android logic lives only inside `zeep-notti-react-native/android`, coupled to the TurboModule (`NottiModule` companion statics, `ReactApplicationContext`, `WritableMap`). Native apps can't use Notti, and every wrapper would duplicate the Kotlin core. Same model as `zeep-notti-ios-sdk` (its AD-001) and OneSignal, where `react-native-onesignal` depends on `OneSignal-Android-SDK`.

## Goals

- **Behavior parity with `react-native-notti` 0.8.0 on Android (AD-002, AD-007).** Same backend contract, same retry/queue/session semantics, In-App Messages included. Success: the ported JUnit suite (536 `@Test` in the RN repo at `v0.8.0`) passes against the new SDK, and a device checklist matches the RN SDK's behavior.
- **Few-line integration for native apps.** Gradle dependency, `google-services.json`, and `Notti.initialize(...)` in `Application.onCreate`, with no `FirebaseMessagingService` or Activity code required (manifest-merged service and process hook, AD-005). Success: the example app receives a push, reports `received`/`opened`/`clicked` and delivers a cold-start click with only `initialize` called.
- **Consumable by the RN SDK.** Published on Maven Central (AD-004) under `com.zeeplabs.notti` (AD-003). Success (M2): the RN repo deletes its Kotlin core under `android/src/main/java/com/notti` and keeps only the TurboModule bridge.
- **Parity with the iOS native SDK.** Where RN 0.8.0 is silent or the iOS SDK made an owner decision that is not platform-specific (facade shape, In-App `url` opening), Android follows iOS (AD-006, AD-008). Success: one parity table per feature spec, with every divergence justified.

## Tech Stack

**Core:**

- Language: Kotlin 2.0.x (RN baseline 2.0.21), JVM target 17
- Platform: Android `minSdk 24`, `compileSdk 36` (RN baseline); `targetSdk` set by the host
- Build: Gradle (Kotlin DSL) + version catalog, Android Gradle Plugin 8.x, one library module producing an AAR
- Persistence: `SharedPreferences` file `notti_prefs`, same keys as RN 0.8.0 for on-device continuity

**Key dependencies (same as RN 0.8.0, minus React Native):**

- `com.google.firebase:firebase-messaging` (FCM token and messages)
- `androidx.lifecycle:lifecycle-process` (`ProcessLifecycleOwner`: foreground/background, heartbeat)
- `com.squareup.okhttp3:okhttp` (HTTP client used by `NottiApiClient`)
- `org.json` (platform) for payloads

**Testing:** JUnit 4 on the JVM, Robolectric where the RN suite already uses it (`RemoteMessage`/`Bundle`, Views), OkHttp `MockWebServer` (+ `okhttp-tls` for https image tests), real `org.json` on the test classpath. This is the infrastructure already proven in the RN repo.

**Reference implementations:**

- `../zeep-notti-react-native/android` at tag `v0.8.0` (`291d5a7`, 2026-10-23): behavior and backend contract source
- `../zeep-notti-ios-sdk`: process (`.specs/`, AD log, port rule, guard scripts, release flow) and cross-platform owner decisions
- `OneSignal-Android-SDK` v5: API shape, manifest-merged components, distribution (reference only)

## Scope

**v1 (0.1.0) includes, parity with RN 0.8.0 (AD-007):**

- `initialize(context, appId, clientKey, options)`; device registration (`POST /v1/apps/{appId}/devices`) and FCM token refresh
- `login` / `logout` (durable server-side unlink, latest-wins coalescing before registration), tags (add/remove, merge cache), `setSubscription`, email/phone set/clear
- Permission request (`POST_NOTIFICATIONS` on API 33+), two-axis `permission_status` and `last_unsubscribed_at`
- Device profile fields `app_version`, `device_os`, `device_model`, `sdk_version`, `timezone_id`, `language`, with echo-based ack and re-send on a new device id
- Session telemetry: first/last session, count, time, 60 s foreground heartbeat, orphan close; opt-in country (`setLocationSharingEnabled`)
- Image pushes: the backend sends `notification.image` (`zeep-notti` `internal/providers/fcm/fcm.go`) and the system tray renders it, with no SDK code (counterpart of the iOS NSE `notti_image_url`); covered by the device checklist
- Engagement events `received` / `opened` / `clicked` with the offline queue and network-triggered flush
- Listeners: notification received (foreground), notification clicked, device id changed; cold-start initial click
- Retry policy: 5 attempts, 2/4/8/16 s backoff; 408/429/5xx/network errors transient
- Manifest-merged `FirebaseMessagingService` and process-start `ContentProvider`; manual forwarding API (`NottiBridge`) for hosts that own their FCM service
- In-App Messages: session fetch, native View layouts, `trackEvent`, click listener, `push_permission` button, own event queue; `url` actions opened in the browser by default (AD-006)
- Kotlin-first public API with Java interop (`@JvmStatic`, SAM listeners, no `suspend`-only entry points)
- Consumer R8/ProGuard rules, example app, CI, README with a Play Data safety section, CHANGELOG

**Explicitly out of scope (v1):**

- iOS/Flutter (separate repos)
- Backend contract changes (the SDK targets `zeep-notti` as-is: >= v0.11.0 push, >= v0.23.0 In-App, >= v0.24.0 logout unlink)
- Notification action buttons: RN 0.8.0 Android does not have them (the RN `notification-action-buttons` spec is not implemented). M3.
- Building notifications in the SDK: as in RN, FCM's `notification` payload is displayed by the system tray while backgrounded
- Privacy consent gating API, Keystore-backed storage for email/phone, `Retry-After` on the core client (M3, same as iOS)
- Expo config plugin (stays in the RN repo)

## Constraints

- **Technical:** the backend HTTP contract and `notti_prefs` keys stay byte-compatible with RN 0.8.0. A device moving from an RN app to its native rewrite (same `applicationId`), or from RN to RN-on-native-SDK, must not re-register as a new device.
- **Technical:** no class may share a fully qualified name with RN's `com.notti.*` (both are on the classpath during M2, AD-003).
- **Technical:** the SDK must not break a host that has its own `FirebaseMessagingService` or another push SDK: the manual API and a documented manifest opt-out must cover it.
- **Technical:** nothing in the process-start hook does I/O, network or SDK work (same rule as RN `NottiInitProvider`).
- **Privacy/LGPD:** email, phone, external user id and country are personal data. Nothing beyond what RN 0.8.0 collects; no PII, tokens or URLs in logs; default log level error.
- **Resources:** solo maintainer (Julio). Behavior stays in lockstep with `zeep-notti-ios-sdk`; until M2 ships, fixes land in RN and here.
