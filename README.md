# Notti Android SDK

Native Android SDK (Kotlin) for [Notti](https://app.zeepnotti.app) push notifications and In-App Messages.

> **Status: pre-release.** Nothing is published yet. The first release, `0.1.0`, targets behavior parity with `react-native-notti` 0.8.0 on Android. Plans live in [`.specs/`](.specs/project/ROADMAP.md).

## Android integration (draft)

> Draft from `push-integration`; the final wording, the public `Notti` API and the device checklist come with `example-and-release`.

### Automatic setup

Add the dependency and your app's `google-services.json` (with the Google Services plugin in your app). The SDK's manifest is merged into your app and brings:

- `NottiFirebaseMessagingService` (`com.google.firebase.MESSAGING_EVENT`, not exported): FCM token refreshes and foreground messages (`received` events).
- `NottiInitProvider` (authority `${applicationId}.com.zeeplabs.notti.initprovider`, not exported): a process-start hook that detects notification taps (`opened` + `clicked`, including the tap that launched the app) and starts the foreground and network observers. It does no network work.
- `NottiPermissionActivity` (translucent, not exported): hosts the Android 13+ notification permission dialog from whatever Activity is in front.
- `android.permission.POST_NOTIFICATIONS`.

No `FirebaseMessagingService` or Activity code is needed. A tapped notification's `data` reaches your app untouched; the SDK never opens a URL or deep link from it.

### Your app already has a `FirebaseMessagingService`

Android delivers FCM messages to one service per app. Remove the SDK's service and forward from yours:

```xml
<!-- AndroidManifest.xml of your app (xmlns:tools="http://schemas.android.com/tools") -->
<service
    android:name="com.zeeplabs.notti.NottiFirebaseMessagingService"
    tools:node="remove" />
```

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // your own handling
        NottiBridge.onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // your own handling
        NottiBridge.onMessageReceived(remoteMessage)
    }
}
```

`NottiBridge` methods are static for Java callers (`NottiBridge.onNewToken(token)`) and are safe no-ops before the SDK has started. `:example-manual-fcm` in this repository is a buildable sample.

### Removing the process-start hook

You can remove `com.zeeplabs.notti.NottiInitProvider` with `tools:node="remove"`, but in this pre-release nothing else installs its hooks, so you lose:

- notification tap detection (no `opened`/`clicked` events, no initial click), including the tap that launches the app;
- foreground/background tracking: sessions after the first, and the registration retry when the app returns to the foreground;
- the flush of queued events when the network comes back (queued events are still sent after the device registers).

The SDK still creates its runtime lazily on initialization and from the FCM service, so `received` events are kept.

### `singleTop` / `singleTask` Activities

A tap on a notification while your app is running delivers a new Intent to `onNewIntent`. The SDK reads the Activity's current Intent, so call `setIntent`:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
}
```

Without it, warm taps on a `singleTop`/`singleTask` Activity are not reported (same as `react-native-notti` 0.8.0).

### Notification permission (Android 13+)

`POST_NOTIFICATIONS` is declared by the SDK. On Android 13+ the permission request shows the system dialog from the Activity in front and reports the answer; with no Activity in front it answers `false` without a dialog. Below Android 13 no runtime permission exists and the request answers `true`.

## License

MIT, see [LICENSE](LICENSE).
