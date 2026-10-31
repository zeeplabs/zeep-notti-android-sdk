# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Public API: `Notti` (`initialize`, `login`, `logout`, `deviceId`, `addDeviceIdChangedListener`, `setSubscription`, `setLocationSharingEnabled`, `logLevel`), `Notti.User` (tags, email, phone) and `Notti.Notifications` (`requestPermission`, received and clicked listeners, `takeInitialClick`), with `NottiOptions`, `NottiNotification`, `NottiLogLevel`, `NottiSubscription` and three listener interfaces. Callable from Java; listeners run on the main thread.
- `Notti.initialize` installs the process hooks when `NottiInitProvider` was removed from the manifest.
- In-App messages, ported from `react-native-notti` 0.8.0: one eligible fetch per session, `session_start` and `event` triggers, four layouts, impression/click/dismiss events queued offline. Public API: `Notti.trackEvent`, `Notti.InAppMessages.addClickListener` with `NottiInAppClick`, `NottiInAppAction` and `NottiInAppClickListener`.
- `NottiOptions.opensInAppUrls` (default `true`): the SDK opens an In-App `url` action with an `http`/`https` value in the browser after the click listeners ran. `react-native-notti` 0.8.0 never opened it.

### Fixed

- Reopening the app from Recents no longer reports the launch tap again as a new `opened` + `clicked` (inherited from `react-native-notti` 0.8.0).
