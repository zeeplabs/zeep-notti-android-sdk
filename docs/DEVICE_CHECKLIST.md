# Device validation checklist for 0.1.0

CI cannot prove the items below: they need a real FCM path, a device or emulator with Google Play services, and the Notti dashboard. Run them with the example apps before the `0.1.0` tag. This is a gate for Julio, not for contributors (EXR-12).

Closing a `STATE.md` todo or a traceability gap an item cites is a **post-checklist step**: tick it there only after the item passes (EXR-13).

## Run

| Field | Value |
| --- | --- |
| Tester | |
| Date | |
| SDK commit | |
| Devices (model, Android version, API level) | |
| Emulator image (if used) | |
| Notti backend release | |

## Prerequisites

- A device on Android 13+ (API 33+) and one on Android 12 or lower (API ≤ 32). Items marked **Emulator OK** can run on an emulator image **with Google Play** instead.
- A Notti app with FCM configured in the backend, and a way to send it a push (dashboard or API).
- `example/notti.properties` (from `notti.properties.sample`) with that app's id and key, and `google-services.json` in `example/` (and in `example-manual-fcm/` for DC-18). Both are git-ignored; never commit them.
- `adb` on the computer. Install with `./gradlew :example:installDebug` unless an item says otherwise.
- The example screen: **Status**, **Permission**, **Identity**, **User**, **Device**, **Notifications**, **In-App** and **Event log** sections. Event log lines also go to `adb logcat -s NottiExample`; SDK lines to `adb logcat -s Notti` (set **Log level** to `DEBUG`).

## How to record results

Tick **Pass** or **Fail** and add one line under `Result:` with what you saw. Do **not** write the app id, client key, push token, device id, email, phone or any tag value here or in a PR: write "present", "absent" or a count. Test data: external user id `checklist-user`, email at `example.invalid`, phone `+5511900000000`.

To kill the app: `adb shell am force-stop com.zeeplabs.notti.example` (a force-stopped app does not receive FCM until launched again; use swipe-away from Recents for the "killed" state below).

---

## Registration and profile

### DC-01. FCM token and device registration — Emulator OK

**Source:** core-port SDK-01/SDK-02 (real FCM path); push-integration SDK-06.
**Device/API:** any.
**Steps:**
1. Fresh install, open the app.
2. Check **Status** shows "Configured" and, within a few seconds, a device id other than "not registered".
**Expected:** Event log has `initialize called`. The device appears in the Notti dashboard for the app, platform `android`, with app version, SDK version `0.1.0`, OS version, model, time zone and language filled.

- [ ] Pass - [ ] Fail
Result:

### DC-02. Not configured state — Emulator OK

**Source:** EXR-04.
**Device/API:** any.
**Steps:** Rename `example/notti.properties`, reinstall, open the app, tap **Login** and **Track event**.
**Expected:** **Status** shows "Not configured…"; the app does not crash; `adb logcat -s Notti` shows the SDK dropping the calls. Restore the file afterwards.

- [ ] Pass - [ ] Fail
Result:

### DC-03. Identity, email, phone and tags — Emulator OK

**Source:** core-port DLOG-*, DPF-17/18 (real backend); README `logout` wording (DLOG-22).
**Device/API:** any.
**Steps:**
1. Type `checklist-user`, tap **Login**.
2. Set email and phone; add tag `plan` = `fixture`; tap **Add sample tags**.
3. Tap **Remove tag**, **Remove sample tags**, **Clear email**, **Clear phone**.
4. Set email and phone again, then tap **Logout**.
**Expected:** after each step the dashboard device shows the change. After **Logout**: no external user id, email and phone empty, remaining tags kept. The event log names each call and never shows a typed value.

- [ ] Pass - [ ] Fail
Result:

### DC-04. Subscription off and on — Emulator OK

**Source:** core-port DPF-14/DPF-16.
**Device/API:** any, permission granted.
**Steps:** Check **Subscribed to push**, then uncheck it; send a push; check it again; send a push.
**Expected:** dashboard shows `subscribed` false then true and a `last_unsubscribed_at` set at the uncheck; the push sent while unsubscribed is not delivered, the second one is.

- [ ] Pass - [ ] Fail
Result:

### DC-05. Location sharing

**Source:** core-port SEGTEL-10..15 (Geocoder on a device).
**Device/API:** a device with location on. Grant the example location permission in system settings beforehand (the SDK never asks; `adb shell pm grant com.zeeplabs.notti.example android.permission.ACCESS_COARSE_LOCATION` works only if the app declares it, so use a host build that does, or record "not run").
**Steps:** Check **Share location**; background and reopen the app (new session); uncheck it.
**Expected:** dashboard shows a country after the new session, and no country after unchecking.

- [ ] Pass - [ ] Fail - [ ] Not run (no location permission in the example)
Result:

### DC-06. Upgrade from the RN 0.8.0 example keeps the device id

**Source:** core-port (device id continuity, same `notti_prefs` keys).
**Device/API:** any.
**Steps:**
1. Build the `react-native-notti` 0.8.0 example with `applicationId` `com.zeeplabs.notti.example` (temporary local change, not committed), same Notti app, install and register; note that a device id is present in the dashboard.
2. Install this repo's example over it (`./gradlew :example:installDebug`, no uninstall).
**Expected:** **Status** shows the same device id as before (compare on screen, do not record it); the dashboard has no new device; pending events are sent.

- [ ] Pass - [ ] Fail
Result:

## Permission

### DC-07. Permission on API 33+: grant

**Source:** push-integration SDK-08 and traceability gap "system dialog through `NottiPermissionActivity`". **Emulator OK.**
**Device/API:** API 33+, fresh install.
**Steps:** **Request notification permission** → Allow.
**Expected:** system dialog over the example; **Permission granted: true**; dashboard `permission_status` granted.

- [ ] Pass - [ ] Fail
Result:

### DC-08. Permission on API 33+: deny, deny twice, settings

**Source:** push-integration SDK-08. **Emulator OK.**
**Device/API:** API 33+, fresh install.
**Steps:** request → Don't allow; request again → Don't allow; request a third time; then enable notifications in system settings and reopen the app.
**Expected:** `false` both times; the third request answers `false` without a dialog (Android stops asking); dashboard `permission_status` denied, then granted after the settings change.

- [ ] Pass - [ ] Fail
Result:

### DC-09. Permission below API 33

**Source:** push-integration SDK-08.
**Device/API:** API ≤ 32. **Emulator OK.**
**Steps:** **Request notification permission**.
**Expected:** no dialog, **Permission granted: true**.

- [ ] Pass - [ ] Fail
Result:

## Push delivery and taps

### DC-10. Push received in foreground

**Source:** push-integration (received path); README limitation "no notification shown in the foreground". **Emulator OK.**
**Steps:** app in front, send a push with a `data` key.
**Expected:** event log `notification received, data keys: [...]`; no notification in the tray; dashboard shows `received` only.

- [ ] Pass - [ ] Fail
Result:

### DC-11. Push in background and killed

**Source:** push-integration. **Emulator OK.**
**Steps:** press Home, send a push; then swipe the app away from Recents, send another.
**Expected:** both shown in the tray. No crash.

- [ ] Pass - [ ] Fail
Result:

### DC-12. Warm tap

**Source:** SDKOPEN-11; push-integration traceability gap "push received/tapped". **Emulator OK.**
**Steps:** app in background, tap a notification.
**Expected:** event log `notification tapped, data keys: [...]`; dashboard shows `opened` and `clicked` for that notification.

- [ ] Pass - [ ] Fail
Result:

### DC-13. Cold-start tap with the provider

**Source:** SDKOPEN-11 (cold start). **Emulator OK.**
**Steps:** swipe the app away, send a push, tap it.
**Expected:** app opens; event log (listener added before `initialize`) shows `notification tapped`; **Take initial click** then shows "none"; dashboard `opened` + `clicked`.

- [ ] Pass - [ ] Fail
Result:

### DC-14. Cold-start tap without the provider

**Source:** push-integration traceability gap "provider removal"; README "Removing the process-start hook".
**Device/API:** any. **Emulator OK.**
**Prerequisites:** temporarily add to `example/src/main/AndroidManifest.xml` `<provider android:name="com.zeeplabs.notti.NottiInitProvider" android:authorities="${applicationId}.com.zeeplabs.notti.initprovider" tools:node="remove" />` (not committed); `initialize` stays in `Application.onCreate`.
**Steps:** as DC-13.
**Expected:** same as DC-13. Revert the manifest afterwards.

- [ ] Pass - [ ] Fail
Result:

### DC-15. `singleTop` with `setIntent`

**Source:** README `singleTop` section (RN 0.8.0 parity). **Emulator OK.**
**Prerequisites:** temporarily set `android:launchMode="singleTop"` on `MainActivity` and override `onNewIntent` with `setIntent(intent)` (not committed).
**Steps:** app in front, pull the shade and tap a notification.
**Expected:** one `notification tapped` line; `opened` + `clicked` in the dashboard. Without `setIntent` (second run) the tap is not reported. Revert afterwards.

- [ ] Pass - [ ] Fail
Result:

### DC-16. Reopening from Recents reports nothing

**Source:** STATE todo "Inherited RN 0.8.0 bug … phantom `opened` + `clicked`" (Recents half, fixed by public-api T3). **Emulator OK.**
**Steps:** cold-start tap a notification (DC-13); press Home; reopen from Recents.
**Expected:** no second `notification tapped` line and no new `opened`/`clicked` in the dashboard. The restore half of that todo stays open (README "Removing the process-start hook").

- [ ] Pass - [ ] Fail
Result:

### DC-17. Image push in the tray

**Source:** owner statement (RN Android shows push images); push-integration.
**Device/API:** a physical device.
**Steps:** send a push with an image, in background and in the killed state; expand each.
**Expected:** the image shows in the expanded notification in both states.

- [ ] Pass - [ ] Fail
Result:

### DC-18. `:example-manual-fcm` receives and forwards

**Source:** push-integration PUSH-15.
**Prerequisites:** `google-services.json` in `example-manual-fcm/`; `./gradlew :example-manual-fcm:installDebug`; grant notification permission in system settings (the app has no screen).
**Steps:** launch the app once (`adb shell monkey -p com.zeeplabs.notti.example.manualfcm 1`), send a push, tap it.
**Expected:** a device registers in the dashboard (token forwarded through `NottiBridge.onNewToken`); the push is `received`, the tap is `opened` + `clicked`.

- [ ] Pass - [ ] Fail
Result:

### DC-19. Events queued offline

**Source:** core-port (event queue, `notti_pending_events`, `notti_pending_inapp_events`). **Emulator OK.**
**Steps:** turn on airplane mode; tap a notification received before; show and close an In-App message if one is cached; turn airplane mode off and wait.
**Expected:** dashboard receives the queued events after reconnecting, in order, once.

- [ ] Pass - [ ] Fail
Result:

## In-App Messages

### DC-20. The four layouts, swipe and close

**Source:** in-app-messages dev log pending item "layouts, swipe" (2026-10-31). **Emulator OK.**
**Prerequisites:** four messages in the dashboard (`top`, `bottom`, `center`, `full`), each triggered by an event name.
**Steps:** for each, type the event name in **Event name**, **Track event**; close one by the close control, one bar by swipe, `center` by a tap on the backdrop; one with auto-dismiss.
**Expected:** each layout renders inside the safe area; bars leave the app interactive; each closing path closes it; dashboard has `impression` and `dismiss` with the right reason.

- [ ] Pass - [ ] Fail
Result:

### DC-21. `url`, `deeplink` and `opensInAppUrls = false`

**Source:** AD-006; in-app-messages dev log pending item "abertura de URL". **Emulator OK.**
**Steps:** tap a `url` button with an `https` link; tap a `deeplink` button. Then set `NottiOptions(opensInAppUrls = false)` in `ExampleConfig.options` (not committed), reinstall, tap the `url` button again.
**Expected:** first `url` tap opens the browser once, after the event log `in-app tap … action: URL`; `deeplink` opens nothing (log line only); with the option off nothing opens. `click` events in the dashboard.

- [ ] Pass - [ ] Fail
Result:

### DC-22. `push_permission` button

**Source:** in-app-messages dev log pending item "`push_permission` (prompt/ajustes)".
**Device/API:** API 33+, fresh install.
**Steps:** show a message with a `push_permission` button, tap it; deny; show it again (new session) and tap it.
**Expected:** first tap shows the system prompt; second opens the app's notification settings. Event log `action: null` for both.

- [ ] Pass - [ ] Fail
Result:

### DC-23. TalkBack on an In-App message

**Source:** in-app-messages (accessibility port).
**Device/API:** physical device with TalkBack on.
**Steps:** show a `center` message; swipe through it.
**Expected:** title, body, each button and the close control are announced; the close control label is in the device language (English or Brazilian Portuguese); double-tap activates.

- [ ] Pass - [ ] Fail
Result:

### DC-24. In-App on cold start

**Source:** in-app-messages dev log pending item "cold start". **Emulator OK.**
**Steps:** a `session_start` message active; swipe the app away; open it.
**Expected:** the message shows once after launch; tapping a button logs `in-app tap` (listener added before `initialize`).

- [ ] Pass - [ ] Fail
Result:

### DC-26. Permission request over a visible In-App message

**Source:** pre-release review PRR-06 (AD-019). **Emulator OK.**
**Device/API:** API 33+, fresh install.
**Steps:** show a `center` message (`session_start`); while it is visible, tap **Request notification permission** in the example (scroll behind the message if needed, or trigger it from a `bottom` message); answer the dialog.
**Expected:** the system dialog shows over the app; after the answer the same message is back over the example, not duplicated, with no second impression in the dashboard; no flicker of the message inside the dialog.

- [ ] Pass - [ ] Fail
Result:

## Build

### DC-25. Minified release build on a device

**Source:** push-integration PUSH-01/PUSH-07 (R8, proven in CI by dex inspection only).
**Steps:** `./gradlew :example:installRelease`; repeat DC-01, DC-12 and DC-20 quickly.
**Expected:** same results as the debug build; no `ClassNotFoundException` in `adb logcat`.

- [ ] Pass - [ ] Fail
Result:

---

## After the run

1. Every Fail becomes an issue or a known limitation in the README before the tag.
2. Items marked Not run are listed under the README's "Device proof" limitation.
3. Tick the `STATE.md` todos and traceability gaps the passed items cite (EXR-13).
