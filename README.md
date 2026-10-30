# Notti Android SDK

Native Android SDK (Kotlin) for [Notti](https://app.zeepnotti.app) push notifications and In-App Messages.

> **Status: pre-release.** Nothing is published yet. The first release, `0.1.0`, targets behavior parity with `react-native-notti` 0.8.0 on Android. Plans live in [`.specs/`](.specs/project/ROADMAP.md).

## Usage

> Draft from `public-api`; the final wording and the device checklist come with `example-and-release`.

### Initialize

Call `Notti.initialize` once, as early as possible, ideally in `Application.onCreate`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notti.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY")
        // Self-hosted Notti: Notti.initialize(this, appId, clientKey, NottiOptions(baseUrl = "https://push.example.invalid"))
    }
}
```

The SDK keeps only the application context. Calling `initialize` again with the same values does nothing; with different values the device registers again. Blank ids or an invalid base URL are logged and ignored. No SDK call throws.

### Identify the user

```kotlin
Notti.login("user-123")
Notti.User.addTag("plan", "premium")
Notti.User.addTags(mapOf("tier" to "gold", "region" to "south"))
Notti.User.removeTag("plan")
Notti.User.setEmail("user@example.invalid")
Notti.User.setPhone("+5511900000000")
Notti.setSubscription(false)            // stop push delivery to this device
Notti.setLocationSharingEnabled(true)   // opt-in country targeting, default off
Notti.logout()                          // unlinks the user and clears email and phone on the backend
```

Call these after `initialize`. Before it, `login`, tags and `setSubscription` are ignored (an error line is logged), while email, phone, location sharing and `logout` are kept and sent at the first registration. The SDK never asks for the location permission: with location sharing on and the permission already granted by your app, it reads the country at the next session start. Turning it off also clears the country on the backend.

### Notifications

```kotlin
// Android 13+: shows the system dialog from the Activity in front.
Notti.Notifications.requestPermission { granted -> /* ... */ }

val received = Notti.Notifications.addReceivedListener { notification ->
    // Foreground pushes: notification.title, notification.body, notification.data
}
val clicked = Notti.Notifications.addClickedListener { notification ->
    // Taps: route with notification.data
}
received.remove() // when the listener is no longer needed
```

Every listener and callback runs on the main thread. A received push with no listener is not replayed. While no clicked listener exists, the SDK keeps the last tap and `Notti.Notifications.takeInitialClick()` returns it once. Add the clicked listener in `Application.onCreate` to get the tap that launched the app as an event; `takeInitialClick()` then returns null.

### Device id

`Notti.deviceId` is the Notti device id, or null until the first registration. `Notti.addDeviceIdChangedListener { id -> }` is called each time the backend assigns a new one.

### Logging

`Notti.logLevel = NottiLogLevel.DEBUG` (default `ERROR`, `NONE` silences the SDK). Logs go to logcat under the tag `Notti` and never contain email, phone, push token, user id, country, tag values or URLs.

### Java

Every member is static:

```java
Notti.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY", new NottiOptions());
Notti.User.addTag("plan", "premium");
NottiSubscription clicked = Notti.Notifications.addClickedListener(notification -> { });
Notti.Notifications.requestPermission(granted -> { });
Notti.setLogLevel(NottiLogLevel.DEBUG);
```

## Android integration (draft)

> Draft from `push-integration`; the final wording and the device checklist come with `example-and-release`.

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

You can remove `com.zeeplabs.notti.NottiInitProvider` with `tools:node="remove"`. `Notti.initialize` then installs the same hooks (tap detection, foreground and network observers), once per process. Where you call it decides whether the tap that launched the app is seen:

- In `Application.onCreate`: same as with the provider.
- In the launch Activity's `onCreate`: the launch tap is detected when the Activity resumes, and reopening the app from Recents is not reported as a tap.
- Later (after the first Activity resumed): the launch tap is lost; later taps are detected.

Limitation without the provider: when Android restores an Activity after killing the process, its launch tap cannot be told apart from a new one when the Activity resumes, so it is reported again (`opened` + `clicked`). Keep the provider, or call `initialize` in `Application.onCreate`, to avoid it.

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
