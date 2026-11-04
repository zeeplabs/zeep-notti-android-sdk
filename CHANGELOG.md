# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Native devices send the profile field `sdk_name: "android"` with `sdk_version`, under the same sync and ack rules (ack stored in the new key `notti_last_synced_sdk_name`).
- Wrapper entry point `Notti.initializeForWrapper` (`@NottiWrapperApi`, opt-in): a wrapper SDK such as `react-native-notti` reports its own `sdk_name` and `sdk_version`. For wrappers only, outside semver.

## [0.1.0] - 2026-11-04

First release of the native Android SDK: behavior parity with `react-native-notti` 0.8.0 on Android, as `br.com.zeeplabs:notti-android` on Maven Central.

### Added

- Device registration with the FCM token, device profile fields (app and SDK version, OS, model, time zone, language), sessions, permission status, subscription, opt-in country, identity (`login`/`logout`), email, phone and tags, ported from `react-native-notti` 0.8.0 with its single-thread executor, retry and backoff, and offline queues.
- Push events `received`, `opened` and `clicked` (a tap reports `opened` and `clicked`), through manifest-merged components: `NottiFirebaseMessagingService`, `NottiInitProvider` and `NottiPermissionActivity`; `NottiBridge` for apps with their own `FirebaseMessagingService`.
- Public API: `Notti` (`initialize`, `login`, `logout`, `deviceId`, `addDeviceIdChangedListener`, `setSubscription`, `setLocationSharingEnabled`, `logLevel`), `Notti.User` (tags, email, phone) and `Notti.Notifications` (`requestPermission`, received and clicked listeners, `takeInitialClick`), with `NottiOptions`, `NottiNotification`, `NottiLogLevel`, `NottiSubscription` and three listener interfaces. Callable from Java; listeners run on the main thread.
- `Notti.initialize` installs the process hooks when `NottiInitProvider` was removed from the manifest.
- In-App messages, ported from `react-native-notti` 0.8.0: one eligible fetch per session, `session_start` and `event` triggers, four layouts, impression/click/dismiss events queued offline. Public API: `Notti.trackEvent`, `Notti.InAppMessages.addClickListener` with `NottiInAppClick`, `NottiInAppAction` and `NottiInAppClickListener`.
- `NottiOptions.opensInAppUrls` (default `true`): the SDK opens an In-App `url` action with an `http`/`https` value in the browser after the click listeners ran. `react-native-notti` 0.8.0 never opened it.

### Fixed

- Reopening the app from Recents no longer reports the launch tap again as a new `opened` + `clicked` (inherited from `react-native-notti` 0.8.0).

### Known limitations

- No consent API; email, phone, external user id and tags are stored unencrypted in `notti_prefs`.
- Device and push-event requests ignore `Retry-After`.
- Firebase shows no notification while the app is in the foreground (the SDK reports `received`).
- `singleTop`/`singleTask` Activities must call `setIntent` in `onNewIntent` for warm taps.
- Without `NottiInitProvider`, a restored Activity's launch tap can be reported again.
- In-App images are capped in bytes, not in decoded pixels.
- Apps with Kotlin code need Kotlin 2.0 or later (`kotlin-stdlib` 2.0.21 in the POM) and `compileSdk` 34 or later.
- `logout` unlinking needs `zeep-notti` v0.24.0 or later; tags survive `logout`.

See the README's "Known limitations" for details.

### Migration from `react-native-notti` 0.8.0

- Same `notti_prefs` file and keys: an app that replaces the React Native module keeps its device id, tags and pending events.
- Package `com.zeeplabs.notti` (React Native: `com.notti`) and provider authority `${applicationId}.com.zeeplabs.notti.initprovider`; both build in one app, but run only one SDK (FCM reaches one service).
- In-App `url` actions with an `http`/`https` value are now opened by the SDK by default; pass `NottiOptions(opensInAppUrls = false)` to keep opening them yourself.
- `logout` clears the external user id on the server and email and phone (on the device and the server); tags stay.
