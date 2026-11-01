# Notti Android SDK

Native Android SDK (Kotlin) for Notti push notifications and In-App Messages.

> **Status: 0.1.0.** Behavior parity with `react-native-notti` 0.8.0 on Android, plus the SDK opening In-App web links. Until the release is published on Maven Central the dependency below does not resolve; check the [device checklist](docs/DEVICE_CHECKLIST.md) and [known limitations](#known-limitations) before adopting it.

## Requirements

- `minSdk` 24 or higher and `compileSdk` 34 or higher (the AAR declares `minCompileSdk` 34).
- Kotlin 2.0 or later if your app has Kotlin code (the POM declares `kotlin-stdlib` 2.0.21). Java-only apps need nothing extra.
- A Firebase project with your app registered, and its `google-services.json`.
- A Notti app id and client key from the Notti dashboard.
- A Notti backend (`zeep-notti`) that accepts what the SDK sends: v0.10.0 or later for the device-profile fields, v0.11.0 or later for the `opened` event, v0.23.0 or later for In-App Messages and v0.24.0 or later for the `logout` unlink (see [Known limitations](#known-limitations)). An older backend rejects those fields or events and the SDK drops them.

## Install

The artifact is on Maven Central:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.zeeplabs:notti-android:0.1.0")
}
```

The SDK brings `firebase-messaging` as an `api` dependency, plus `okhttp`, `androidx.core`, `androidx.activity`, `androidx.lifecycle-process` and `androidx.annotation`, at the versions `react-native-notti` 0.8.0 resolves, so it does not force upgrades on your app. A newer version in your app wins.

## Firebase setup

Put your app's `google-services.json` in the app module and apply the Google Services plugin there (never in a library):

```kotlin
// build.gradle.kts (root)
plugins {
    id("com.google.gms.google-services") version "4.5.0" apply false
}

// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}
```

Without it Firebase cannot start: the SDK logs why it cannot get a push token, the device never registers, and your app keeps running. Keep `google-services.json` out of version control if your policy treats it as configuration.

## Initialize

Call `Notti.initialize` once, as early as possible, ideally in `Application.onCreate`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notti.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY")
    }
}
```

The SDK talks to `https://app.zeepnotti.app` unless you pass your own host:

```kotlin
Notti.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY", NottiOptions(baseUrl = "https://push.example.invalid"))
```

`NottiOptions` has two fields: `baseUrl` (null for the default host) and `opensInAppUrls` (default true, see [In-App Messages](#in-app-messages)). The SDK keeps only the application context. Calling `initialize` again with the same values does nothing; with different values the device registers again. Blank or `null` ids or an invalid base URL are logged and ignored. No SDK call throws, also for `null` text, map or list arguments from Java (listener and callback arguments must not be `null`).

## Notification permission

`POST_NOTIFICATIONS` is declared by the SDK.

```kotlin
Notti.Notifications.requestPermission { granted -> /* update your UI */ }
```

On Android 13+ the request shows the system dialog from the Activity in front and reports the answer. With the app in the background, or before an Activity has resumed, it answers `false` without a dialog, and the request does not count as asked (a later `push_permission` button still shows the prompt). Below Android 13 no runtime permission exists and the request answers `true`. The SDK reports the permission state to the backend (`permission_status`) when it changes.

## Identity and tags

```kotlin
Notti.login("user-123")
Notti.User.addTag("plan", "premium")
Notti.User.addTags(mapOf("tier" to "gold", "region" to "south"))
Notti.User.removeTag("plan")
Notti.User.removeTags(listOf("tier", "region"))
Notti.User.setEmail("user@example.invalid")
Notti.User.setPhone("+5511900000000")
Notti.User.clearEmail()
Notti.User.clearPhone()
Notti.setSubscription(false)            // stop push delivery to this device
Notti.setLocationSharingEnabled(true)   // opt-in country targeting, default off
Notti.logout()
```

`login` links the device to your own user id. Email, phone and tags are optional and are sent only when you set them. `logout` clears the external user id on the device and sends `external_user_id: null` to unlink it on the server, and clears email and phone on the device (each clear is also sent to the server); tags and the subscription stay. The unlink is kept on the device until the backend acknowledges it: a `logout` made offline, before `Notti.initialize`, or whose request failed is sent again at the next registration (a later `login` replaces it).

Call these after `initialize`. Before it, `login`, tags and `setSubscription` are ignored (an error line is logged), while email, phone, location sharing and `logout` are kept and sent at the first registration. The SDK never asks for the location permission: with location sharing on and the permission already granted by your app, it reads the country at the next session start. Turning it off also clears the country on the backend.

## Listeners and the initial click

```kotlin
val received = Notti.Notifications.addReceivedListener { notification ->
    // A push arrived while the app runs: notification.title, notification.body, notification.data
}
val clicked = Notti.Notifications.addClickedListener { notification ->
    // A notification was tapped: route with notification.data
}
received.remove() // when the listener is no longer needed

val deviceIds = Notti.addDeviceIdChangedListener { id -> /* the backend assigned a new device id */ }
val current: String? = Notti.deviceId // null until the first registration
```

Every listener and callback runs on the main thread. A received push with no listener is not replayed. While no clicked listener exists, the SDK keeps the last tap and `Notti.Notifications.takeInitialClick()` returns it once. Add the clicked listener in `Application.onCreate`, before `initialize`, to get the tap that launched the app as an event; `takeInitialClick()` then returns null.

A tap reports two events to Notti, `opened` and `clicked`, under the notification id. A tapped notification's `data` reaches your app untouched; the SDK never opens a URL or deep link from it.

## In-App Messages

> **Links.** By default the SDK opens an In-App `url` action whose value is an `http` or `https` link in the browser, after your click listeners ran: **do not open it again in the listener**. It never opens a `deeplink`, nor a `url` with any other scheme (`tel:`, `mailto:`, custom schemes): those are yours, through the click listener. Without a listener, a tap on such a button still reports `click` to Notti and closes the message, nothing opens, and the SDK logs one warning. To open web links yourself, pass `NottiOptions(opensInAppUrls = false)` to `Notti.initialize`.

Messages are authored in the Notti dashboard and shown by the SDK over the current Activity, with Android Views. Once per foreground session, after this process registered the device, the SDK asks the backend which messages this device can see. It shows the first `session_start` message at once and keeps `event` messages in memory until your app reports a matching event. At most one message is visible, and each message shows at most once per session; whether it shows again later is the backend's decision.

```kotlin
// Before Notti.initialize, so a message shown right after launch already has a listener.
val inAppClicks = Notti.InAppMessages.addClickListener { click ->
    // null for a `push_permission` button and for a button without a valid action.
    val action = click.action ?: return@addClickListener
    when (action.type) {
        NottiInAppAction.Type.DEEPLINK -> { /* route action.value inside the app */ }
        NottiInAppAction.Type.URL -> { /* opened by the SDK after this listener returns */ }
        else -> { /* a type added in a later version */ }
    }
}

// Shows the oldest waiting message whose trigger is this event name (exact, case-sensitive).
Notti.trackEvent("checkout_completed")

inAppClicks.remove()
```

- **`Notti.trackEvent`** matches on the device only: the name is never sent, stored or logged. A blank name does nothing. A call before `initialize`, or in the background after a session ended, is ignored. A call after `initialize` and before the first session opens is remembered for that session (up to 32 distinct names). Any thread.
- **The click listener** runs on the main thread, before the message closes, for a tap on a button, on a tappable image, or on the body of a `top` or `bottom` message that has buttons (it acts as the first button). `NottiInAppClick` carries `messageId`, `buttonId` (`image` for the image) and `action`: a `NottiInAppAction` with `type` (`URL` or `DEEPLINK`) and the untouched `value`, or null. Public enums (`NottiInAppAction.Type`, `NottiLogLevel`) may gain values in a minor release, so keep an `else` branch. Listeners added before `initialize` are kept; a click with no listener is not replayed.
- **Web links.** With `opensInAppUrls` true (the default), after the listeners ran and the message closed, the SDK starts one `ACTION_VIEW` intent for a `url` action whose value is an `http` or `https` URL (any letter case) with a host and no whitespace, from the Activity in front (or in a new task when there is none). Any other value is not opened and logs one fixed line at level `DEBUG`; a failed open (no browser) logs one fixed warning and is not retried. The SDK never logs the link. Every valid `initialize` sets the option to the value it carries, like `baseUrl`, without registering again: a later call with `NottiOptions()` turns it back on, so pass the same options on every call.
- **`push_permission` buttons** run the permission flow instead of a link: the listener gets the click with `action == null`, the message closes, and then the SDK shows the system prompt if it never asked (Android 13+), opens the app's notification settings if notifications are off and it already asked (or below Android 13), and does nothing if they are on. Android cannot tell "never asked" from "permanently denied": after the SDK has shown the prompt once, later taps open the settings. Apart from that settings screen, the only URLs the SDK opens are the `url` actions above (`scripts/check-no-url-open.sh` allowlists one file).
- **Events.** The SDK reports `impression`, `click` (with the button id) and `dismiss` (with the reason). They are queued in the app's `notti_prefs` file (key `notti_pending_inapp_events`), sent in order, and dropped after 7 days; past 100 queued events the oldest is dropped.
- **Presentation.** `top` and `bottom` bars (the app underneath stays interactive), a `center` card over a dimmed backdrop, and `full` inside the safe area. A close control is always present; bars also close with a swipe, `center` with a tap on the backdrop, and an optional auto-dismiss timer closes the message on its own. Title and body are plain text. The close control is labelled in English and Brazilian Portuguese, and text follows the system font scale.
- **Data sent.** One `POST {baseUrl}/v1/apps/{appId}/in-app-messages/eligible` per session (`device_id`, `token`) and one `POST .../in-app-messages/{id}/events` per event (`event_id`, `type`, `button_id`, `reason`, `occurred_at`), authenticated with the client key. A message image is downloaded by the device from the author's host: `https` only, no client key, token or cookies, at most 5 MiB and 10 s, kept in memory only.

## Logging

`Notti.logLevel = NottiLogLevel.DEBUG` (levels `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `NONE`; default `ERROR`, `NONE` silences the SDK). Logs go to logcat under the tag `Notti` and never contain email, phone, push token, user id, country, tag values or URLs; errors are logged by class or code, not by message.

## Notification icon and color

Push notifications are drawn by Firebase Messaging, not by the SDK. Set the small icon, accent color and default channel with Firebase's own meta-data in your app's manifest:

```xml
<application>
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_icon"
        android:resource="@drawable/ic_notification" />
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_color"
        android:resource="@color/notification_accent" />
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_channel_id"
        android:value="@string/default_notification_channel_id" />
</application>
```

Without an icon Android uses the app icon, which renders as a plain square on most devices. A push with an image shows it in the expanded notification (Firebase draws it).

## Android integration

### Automatic setup

The SDK's manifest is merged into your app and brings:

- `NottiFirebaseMessagingService` (`com.google.firebase.MESSAGING_EVENT`, not exported): FCM token refreshes and pushes received while the app runs (`received` events).
- `NottiInitProvider` (authority `${applicationId}.com.zeeplabs.notti.initprovider`, not exported): a process-start hook that detects notification taps (`opened` + `clicked`, including the tap that launched the app) and starts the foreground and network observers. It does no network work.
- `NottiPermissionActivity` (translucent, not exported): hosts the Android 13+ notification permission dialog from whatever Activity is in front.
- `android.permission.POST_NOTIFICATIONS`.

No `FirebaseMessagingService` or Activity code is needed.

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

`NottiBridge` methods are static for Java callers (`NottiBridge.onNewToken(token)`) and are safe no-ops before the SDK has started. `example-manual-fcm/` in this repository is a buildable sample.

### Removing the process-start hook

You can remove `com.zeeplabs.notti.NottiInitProvider` with `tools:node="remove"`. `Notti.initialize` then installs the same hooks (tap detection, foreground and network observers), once per process. Where you call it decides whether the tap that launched the app is seen:

- In `Application.onCreate`: same as with the provider.
- In the launch Activity's `onCreate`: the launch tap is detected when the Activity resumes, and reopening the app from Recents is not reported as a tap.
- Later (after the first Activity resumed): the launch tap is lost; later taps are detected.

Without the provider, when Android restores an Activity after killing the process, its launch tap cannot be told apart from a new one when the Activity resumes, so it is reported again (`opened` + `clicked`). Keep the provider, or call `initialize` in `Application.onCreate`, to avoid it.

### `singleTop` / `singleTask` Activities

A tap on a notification while your app is running delivers a new Intent to `onNewIntent`. The SDK reads the Activity's current Intent, so call `setIntent`:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
}
```

Without it, warm taps on a `singleTop`/`singleTask` Activity are not reported (same as `react-native-notti` 0.8.0).

### R8

No rules are needed. The AAR ships an empty `consumer-rules.pro`: R8 keeps the manifest components through AGP's own rules, and your code references `NottiBridge` directly. CI builds a minified example app and checks that the SDK's components survive.

## Java

Every member is static:

```java
Notti.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY", new NottiOptions());
Notti.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY", new NottiOptions(null, false)); // you open web links
Notti.login("user-123");
Notti.User.addTag("plan", "premium");
NottiSubscription clicked = Notti.Notifications.addClickedListener(notification -> { });
Notti.Notifications.requestPermission(granted -> { });
NottiSubscription inApp = Notti.InAppMessages.addClickListener(click -> { });
Notti.trackEvent("checkout_completed");
Notti.setLogLevel(NottiLogLevel.DEBUG);
String deviceId = Notti.getDeviceId();
```

Text, map and list arguments accept `null`: the call is logged by method name and ignored, and `null` keys, values or list items are dropped. Listener and callback arguments must not be `null`.

## Data the SDK handles

This section is derived from the code (`NottiApiClient`, `NottiCore`, `NottiAndroidProviders`, `NottiDeviceStore`). Re-read it against `notti/src/main` before each release.

### Sent to the Notti backend

Requests go to the host set at [Initialize](#initialize): `/v1/apps/<appId>/devices` (registration), `/v1/apps/<appId>/devices/<deviceId>` (every later update, so the device id travels in the path), `/v1/apps/<appId>/notifications/<id>/events`, `/v1/apps/<appId>/in-app-messages/eligible` and `/v1/apps/<appId>/in-app-messages/<messageId>/events`, authenticated with your client key (`Authorization: Bearer`). Every device update also carries the push `token`.

| When | Fields |
|---|---|
| Registration | `token` (FCM push token), `platform` (`android`) |
| `login`, `logout` | `external_user_id` (`null` on `logout`) |
| `setEmail`, `setPhone`, `clearEmail`, `clearPhone`, `logout` | `email`, `phone` (`null` on a clear) |
| `addTag`, `addTags`, `removeTag`, `removeTags` | `tags` (your own key/value pairs) |
| Permission and `setSubscription` | `permission_status`, `subscribed`, `last_unsubscribed_at` |
| Device profile, synced when changed | `app_version`, `sdk_version`, `device_os`, `device_model`, `timezone_id`, `language` |
| Sessions | `first_session_at`, `last_session_at`, `session_count`, `session_time_seconds` |
| Location sharing on, and your app already holds location permission | `country` only (`null` when you turn sharing off) |
| A push is received, opened or clicked | `delivery_id`, `type` (`received`, `opened`, `clicked`), `token`, reported under the notification id |
| In-App fetch, once per foreground session | `device_id`, `token` |
| An In-App message is shown, tapped or closed | `device_id`, `token`, `event_id`, `type` (`impression`, `click`, `dismiss`), `occurred_at`, `button_id` (click only, the author's id), `reason` (dismiss only, a fixed value), reported under the message id |

Do not put personal data in tag keys or values unless your own basis for processing covers it. Country is resolved on the device with Android's `Geocoder` from a location your app's permission already allows; the SDK never asks for location permission and sends only the country code. Geocoding may use a network service provided by the device.

### Stored on the device

`SharedPreferences` file `notti_prefs`, unencrypted, in your app's private storage. The keys are:

`notti_device_id`, `notti_last_token`, `notti_external_user_id`, `notti_subscribed`, `notti_tag_<key>` (one entry per tag), `notti_app_version`, `notti_first_session_at_ms`, `notti_last_session_at_ms`, `notti_session_count`, `notti_session_time_ms`, `notti_session_started_at_ms`, `notti_last_foreground_at_ms`, `notti_location_sharing_enabled`, `notti_pending_country_clear`, `notti_pending_external_user_id_clear`, `notti_last_synced_country`, `notti_last_synced_device_os`, `notti_last_synced_device_model`, `notti_last_synced_sdk_version`, `notti_last_synced_timezone_id`, `notti_last_synced_language`, `notti_last_synced_permission_status`, `notti_last_unsubscribed_at_ms`, `notti_pending_unsubscribe_at_ms`, `notti_pending_permission_unsubscribe_at_ms`, `notti_permission_requested`, `notti_email`, `notti_phone`, `notti_last_synced_email`, `notti_last_synced_phone`, `notti_profile_field_ack_migrated`, `notti_pending_events`, `notti_pending_inapp_events`.

The file name and keys are the same as in `react-native-notti` 0.8.0, so an app that moves from the React Native module keeps its device id and pending events. `notti_pending_external_user_id_clear` is a flag with no personal data: a `logout` unlink the backend has not acknowledged yet. `notti_pending_inapp_events` is the In-App event queue (message id, event type, button id or dismiss reason, time and app id; no token, device id, message text or event name). Whether the file is backed up follows your app's `allowBackup` and backup rules; the SDK sets none.

### Google Play Data safety

**To confirm by the app owner.** These are the answers the SDK implies, based on the table above. Your app's form must also cover your own collection, and whether a type applies depends on which calls you make.

| Play data type | Collected when | Purpose |
|---|---|---|
| Device or other IDs | always (Notti device id, FCM push token) | App functionality |
| Personal info: User IDs | you call `login` | App functionality |
| Personal info: Email address | you call `setEmail` | App functionality |
| Personal info: Phone number | you call `setPhone` | App functionality |
| Location: Approximate location | you enable location sharing (country only) | App functionality |
| App activity: App interactions | always (push `received`/`opened`/`clicked`, In-App `impression`/`click`/`dismiss`, session counts) | App functionality, Analytics |
| App activity: Other actions | you set tags | App functionality |
| App info and performance: Other app performance data | always (app version, SDK version, OS version, device model, time zone, language) | App functionality |

- **Encrypted in transit:** yes with the default host or any `https` base URL. The SDK also accepts an `http` base URL; do not use one in production.
- **Shared with third parties:** the data goes to your Notti backend, which processes it for you; whether that counts as sharing in your form depends on your contract with the Notti operator. The SDK sends nothing to other parties (Firebase receives only what Firebase Messaging itself needs to deliver pushes).
- **Optional or required:** the device id, push token, interactions and app info are required for push delivery; user id, email, phone, tags and location are optional and only collected when your app sets them.
- **Deletion:** the SDK has no delete call. Deletion requests are handled in your Notti backend.

### LGPD

This README describes what the SDK does. It is not legal advice and does not state that your use is compliant with the LGPD. Before shipping, have your legal and privacy teams review the data above, the legal basis, the notice shown to users, and retention and deletion in your Notti backend. That review includes In-App Messages: each In-App request carries the device id and the push token, and each message the user sees, taps or closes becomes an event linked to the device record. `trackEvent` names stay on the device.

## Known limitations

- **No consent API in 0.1.0.** There is no call to hold collection until the user consents. Gate `Notti.initialize` and `Notti.login` yourself.
- **Personal data in plain `SharedPreferences`.** Email, phone, external user id and tags are stored unencrypted in `notti_prefs`, in your app's private storage.
- **No `Retry-After` on device requests.** A device or push-event request is tried up to 5 times, with 2, 4, 8 and 16 second waits. Network errors, 5xx, 408 and 429 are retried; other 4xx are final. A `Retry-After` header is ignored there (the In-App client honors it).
- **No notification shown in the foreground.** While your app is in the foreground, Firebase does not show a notification-type push; the SDK reports `received` and calls your received listeners, and showing it is up to you.
- **`setIntent` for warm taps.** A `singleTop`/`singleTask` Activity that does not call `setIntent` in `onNewIntent` loses warm taps (see [`singleTop` / `singleTask`](#singletop--singletask-activities)).
- **Removing `NottiInitProvider`** changes which launch taps are seen and can report a restored Activity's launch tap twice (see [Removing the process-start hook](#removing-the-process-start-hook)).
- **A web link opened twice.** With `opensInAppUrls` true, a click listener that also opens a `url` action opens it a second time. Open it in the listener only with `NottiOptions(opensInAppUrls = false)`.
- **No pixel-size cap on In-App images.** Downloads are capped at 5 MiB and 10 s, but a small file that decodes to a very large bitmap is not rejected.
- **Next to `react-native-notti`.** The packages differ (`com.zeeplabs.notti` here, `com.notti` there) and the provider authorities differ, so both build in one app, but Android delivers FCM to one service only: do not run both SDKs in one app.
- **Kotlin 2.0 or later.** The POM declares `kotlin-stdlib` 2.0.21 at compile scope; an app compiling with Kotlin 1.9 or older is not supported.
- **`logout` and re-registration need `zeep-notti` v0.24.0 or later.** Against an older backend, the unlink answers 2xx but the backend keeps the stored value, so the device stays linked to the previous user until the next launch; the SDK counts the 2xx as delivered and does not retry. An older backend also resets `external_user_id` and tags at every launch registration. `react-native-notti` 0.8.0 has the same limits.
- **Tags survive `logout`.** Clear them yourself (`removeTags`) if they describe the user.
- **Device proof.** Every item in the [device checklist](docs/DEVICE_CHECKLIST.md) not yet marked passed is a limitation of this release.

## Example app

`example/` is one screen that calls every public member, with a redacted event log; `example-manual-fcm/` is a build proof for an app with its own `FirebaseMessagingService`. To run them against your Notti app:

1. Copy `example/notti.properties.sample` to `example/notti.properties` and set `notti.appId`, `notti.clientKey` and, optionally, `notti.baseUrl`. Both apps read it.
2. Put your `google-services.json` in `example/` (and in `example-manual-fcm/` for that app), registered for `com.zeeplabs.notti.example` and `com.zeeplabs.notti.example.manualfcm`.
3. `./gradlew :example:installDebug`.

Both files are git-ignored and CI fails if one is committed. Without `notti.properties` the app shows "Not configured" and never calls `Notti.initialize`; without `google-services.json` it builds and runs, and the SDK logs why it cannot register. `scripts/check-example-coverage.sh` fails CI when a public member is missing from the example.

## License

MIT, see [LICENSE](LICENSE).
