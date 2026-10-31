# In-App Messages Specification

## Problem Statement

The Android SDK has the In-App data layer (models, event queue, API calls, core hooks) but no engine: nothing fetches eligible messages per session, shows them, reports impressions/clicks/dismisses, or lets the host react to a click. RN 0.8.0 ships all of it on Android (`NottiInApp*.kt`, ~2.9k lines, 276 tests) and AD-007 makes it part of 0.1.0. Without it, M2 would regress RN apps. This feature ports the RN engine, exposes it through the `Notti` facade, and adds the one cross-SDK behavior already decided: the SDK opens http/https `url` actions (AD-006, iOS AD-020).

## Goals

- [x] Every RN 0.8.0 Android In-App behavior works in this SDK, proved by the RN In-App tests ported 1:1 (227 still to port + 9 `NottiPushPermissionActionTest`, same count per class) plus the RN `NottiModuleTest` In-App wiring cases re-expressed against the facade.
- [x] Public surface: `Notti.trackEvent`, `Notti.InAppMessages.addClickListener`, `NottiInAppClick`, `NottiInAppAction`, and the `opensInAppUrls` option on `NottiOptions`; usable from Java, guarded by `apiCheck`.
- [x] `url` actions with http/https open in the browser after the listeners run (AD-006); everything else is never opened. Enforced by `scripts/check-no-url-open.sh` with exactly one allowlisted file.
- [x] No PII in any log line of the engine at any level (AD-011): no message id, event name, URL, device id or token.
- [x] The engine works with and without `NottiInitProvider` (hooks installed lazily by `initialize`, as in `public-api`).

## Out of Scope

| Feature | Reason |
| --- | --- |
| Opening `deeplink` actions, or URLs with any scheme other than http/https | AD-006; the host gets the click and decides |
| In-app browser (Custom Tabs), a "universal link" option | iOS in-app-open-url out of scope; Custom Tabs would add a dependency (AD-013) |
| Compose renderer, host-provided view, display lifecycle callbacks (shown/dismissed) | Not in RN 0.8.0 nor iOS M1; ROADMAP "Compose-native In-App renderer" is post-M1 |
| HTML/Markdown, custom fonts, carousel, per-locale content | Not in RN 0.8.0 |
| Persisted "already shown" store, frequency caps, device-side targeting, sending event names to the server | Not in RN 0.8.0; names stay local (RN, iOS IAM-08) |
| Pixel-dimension cap for images (decompression bomb) | RN and iOS cap bytes only (iOS STATE N5, open). Port as is; recorded as a cross-SDK todo needing its own spec (Julio, 2026-10-31) |
| RN bridge passing `opensInAppUrls = false` | `rn-consumes-native-sdk` (M2) |
| Per-SDK minimum version for `push_permission` on the backend | `zeep-notti` (iOS STATE:295) |

---

## Parity Table

| Capability | RN 0.8.0 Android | iOS SDK | Android (this spec) |
| --- | --- | --- | --- |
| Track event | `trackEvent(name)` top level | `Notti.trackEvent(_:)` top level | `Notti.trackEvent(name)` |
| Click listener | `InAppMessages.addClickListener`, payload `{messageId, buttonId, action: {type,value} \| null}`, JS thread | `Notti.InAppMessages.addClickListener`, `NottiInAppClick`, main | `Notti.InAppMessages.addClickListener(NottiInAppClickListener)`, `NottiInAppClick`, main, multi-listener |
| Click with no live listener | dropped, warn line | warn only when the SDK did not handle the tap | iOS rule (IOU-05) |
| `url` action | never opened | http/https opened after listeners + close (AD-020) | same as iOS (AD-006) |
| URL-open option | none | `opensInAppURLs: Bool = true`, every valid `initialize` sets it | `NottiOptions.opensInAppUrls = true`, same rule |
| `push_permission` button | action `null` to JS, no warn, prompt or settings after close | same (AD-019) | same; uses `NottiPermissionRequester` instead of RN `PermissionAwareActivity` |
| Engine, layouts, events, images, accessibility | RN | aligned with RN (iOS design parity checklist) | RN port (AD-002) |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Behavior source | RN 0.8.0 `android/` at `v0.8.0` for the engine; iOS for facade and AD-006 rules | AD-002, AD-008 | y (AD) |
| `trackEvent` placement | `Notti.trackEvent(name)` top level, `@JvmStatic` | RN and iOS both top level | y (parity) |
| Click payload | `class NottiInAppClick` (internal ctor) `messageId: String`, `buttonId: String` (`"image"` for the image), `action: NottiInAppAction?`; `class NottiInAppAction` `type: Type` (`URL`, `DEEPLINK`), `value: String` untouched. `action` null for `push_permission` and buttons without a valid action | RN payload, iOS shape; classes not data classes (ABI, as `NottiNotification`) | y (Julio, 2026-10-31) |
| Listener type | `fun interface NottiInAppClickListener { fun onClick(click: NottiInAppClick) }`, returns `NottiSubscription` | Same pattern as the other listeners (`public-api` D5) | y (Julio, 2026-10-31) |
| Option name and default | `NottiOptions(baseUrl: String? = null, opensInAppUrls: Boolean = true)`, `@JvmOverloads` adds the overload; each valid `initialize` applies the value it carries, also when the config is otherwise identical (no re-registration) | AD-006, iOS PRF-08 and D1 | y (AD-006; name Julio, 2026-10-31) |
| How a URL opens | `Intent(ACTION_VIEW, uri)` from the resumed Activity, `FLAG_ACTIVITY_NEW_TASK` from the application context when none; `ActivityNotFoundException` and any other failure: one fixed warn line without the URL, no retry | AD-006 | y (AD-006) |
| URL accepted | scheme `http`/`https` case-insensitive, non-empty host, value with no whitespace or control characters, checked before parsing; `Uri` built from the value unchanged | AD-006, iOS IOU-02 | y |
| Guard | `check-no-url-open.sh` allowlists only the URL opener file. The push-permission settings jump uses `ACTION_APP_NOTIFICATION_SETTINGS`/`ACTION_APPLICATION_DETAILS_SETTINGS` and does not match the guard pattern; the guard pattern is extended with `Intent.parseUri` and `startActivity(` with a `Uri` argument is out of reach of a text check (documented limitation, iOS M5) | AD-006 says two files; only one actually matches. [Provável] simpler than allowlisting a file that never matches | y (Julio, 2026-10-31; AD-006 amended) |
| Where the engine lives | `NottiInAppSetup.install(application)` called from `NottiProcessHooks.installOnce` (provider or lazy `initialize`); the coordinator is owned by the process, not by `NottiRuntime`; runtime passes `NottiInApp.coreHooks` to the core and sets `inAppLifecycle` | RN installs from `NottiInitProvider`; core-port design left both seams | y (Julio, 2026-10-31) |
| Initialize order | `Notti.initialize` calls In-App `onInitialized(appId, key, normalizedBaseUrl)` before the core initializes, as RN `NottiModule.kt:213-225` | AD-002 | y (RN) |
| Logging | RN `Log.w(NAME, …)` lines go through `NottiLogger.warn` with the same fixed text; the setup failure logs the exception class, not `t.message` | AD-011 | y (AD) |

---

## User Stories

### P1: Messages shown per session ⭐ MVP

**User Story**: As a product team using Notti, I want eligible In-App messages to appear when a user opens the app so that campaigns reach active users.

**Acceptance Criteria** (all RN 0.8.0 behavior, proved by the ported tests):

1. **INAPP-01** WHEN a session opens and the device registered in this process THEN the engine SHALL flush the In-App event queue once and call `eligible` once for that session, following RN's retry, 401, 403, 408, 429 and late-response rules (`NottiInAppCoordinatorTest`, `NottiInAppSessionTest`).
2. **INAPP-02** WHEN the response has a renderable `session_start` message THEN the engine SHALL show the first one; `event` messages SHALL be held and shown on an exact, case-sensitive `trackEvent` match; one message visible at a time; each id at most once per session.
3. **INAPP-03** WHEN a message is shown THEN it SHALL render with RN's four layouts, style tokens, metrics, safe-area handling, swipe, body tap, close control and accessibility (`NottiInAppViewFactoryTest`, `NottiInAppStyleTest`, `NottiInAppPresenterTest`, Accessibility).
4. **INAPP-04** WHEN a message has an https `image_url` THEN the image SHALL load with RN's dedicated client (no cookies, no cache, no SSL redirects, 10 s, 5 MB, downsampled, LRU by bytes) and the message SHALL show without the image after the 3 s budget (`NottiInAppImageLoaderTest`).
5. **INAPP-05** WHEN a message is shown, clicked or dismissed THEN the engine SHALL queue one impression, click or dismiss (`close_button`, `swipe`, `outside_tap`, `auto`) in `notti_pending_inapp_events` with a stable UUID and send them in FIFO order with RN's rules.
6. **INAPP-06** WHEN `dismiss_after_seconds` is set THEN the message SHALL close after that visible time with `reason: auto`, the clock paused while backgrounded or detached.
7. **INAPP-07** WHEN the app goes to background, returns, or the network comes back THEN the engine SHALL end/open sessions and resume transient give-ups as RN does, through `NottiInAppLifecycle` from the existing observers.
8. **INAPP-08** WHEN the host removed `NottiInitProvider` and calls `Notti.initialize` THEN the engine SHALL be installed and work the same.

**Independent Test**: `NottiInAppEndToEndTest` (ported) against MockWebServer shows a session message, reports impression and dismiss.

---

### P1: Host events and clicks ⭐ MVP

**User Story**: As a host developer, I want to trigger messages from app events and react to button clicks so that campaigns can drive in-app navigation.

**Acceptance Criteria**:

1. **INAPP-09** WHEN the host calls `Notti.trackEvent(name)` from any thread THEN the engine SHALL match it locally (RN rules: early names before the fetch remembered up to 32 distinct; blank name or before `initialize` no-op). The name SHALL never be sent, stored or logged.
2. **INAPP-10** WHEN a button, the tappable image or a bar body is tapped THEN every registered click listener SHALL receive one `NottiInAppClick` on the main thread, in registration order, before the message closes; a throwing listener SHALL not stop the others (same `NottiListeners` as `public-api`).
3. **INAPP-11** WHEN a click is not handled THEN the engine SHALL log one fixed warn line; "not handled" means no listener registered AND the SDK did not open a URL AND the action is not `push_permission` (iOS IOU-05).
4. **INAPP-12** WHEN a listener is removed THEN it SHALL not receive later clicks; `remove()` idempotent.

**Independent Test**: register two listeners, tap a button in a Robolectric presenter, both get the payload on main, then the message closes and one click is queued.

---

### P1: URL actions opened by the SDK (AD-006) ⭐ MVP

**User Story**: As a campaign author, I want a button with a web link to open it without the host writing code so that In-App works the same on iOS and Android.

**Acceptance Criteria**:

1. **INAPP-13** WHEN a `url` action with an accepted http/https value is tapped and `opensInAppUrls` is true THEN, after the listeners and the close, the SDK SHALL start one `ACTION_VIEW` intent for that value on the main thread.
2. **INAPP-14** WHEN the value is not accepted (`tel:`, `mailto:`, custom scheme, `javascript:`, no host, whitespace, empty) or the action is `deeplink` THEN the SDK SHALL not open it; the click still reaches the listeners untouched; at most one fixed debug line without the value.
3. **INAPP-15** WHEN `opensInAppUrls` is false THEN no URL SHALL be opened and nothing logged for it.
4. **INAPP-16** WHEN opening fails (no browser, exception) THEN the SDK SHALL log one fixed warn line without the URL and not retry.
5. **INAPP-17** WHEN `initialize` is called again with only `opensInAppUrls` changed THEN the new value SHALL apply without a new registration.
6. **INAPP-18** WHEN the guard script runs THEN any URL-open call outside the URL opener file SHALL fail CI.

**Independent Test**: Robolectric `ShadowApplication.getNextStartedActivity()` shows one `ACTION_VIEW` for an https action and none for `deeplink`, a `tel:` URL, or with the option off.

---

### P1: Push-permission button ⭐ MVP

**User Story**: As a campaign author, I want a button that asks for push permission so that users opt in from a message.

**Acceptance Criteria**:

1. **INAPP-19** WHEN a `push_permission` button is tapped THEN the listeners SHALL get the click with `action == null`, no no-listener warn, the message closes, then the SDK SHALL show the system prompt (API 33+, not asked before, an Activity available) through `NottiPermissionRequester`, otherwise open the app notification settings, as RN `NottiPushPermissionAction` (`NottiPushPermissionActionTest`, 9).

---

### P2: Documentation

1. **INAPP-20** WHEN this feature ships THEN README SHALL document `trackEvent`, the click listener, `opensInAppUrls` (with the double-open warning when a listener also opens the link), the data the engine stores (`notti_pending_inapp_events`), and CHANGELOG SHALL list the additions.

---

## Edge Cases

- WHEN `trackEvent` or `addClickListener` is called before `initialize` THEN `trackEvent` SHALL be a no-op (RN) and the listener SHALL still be registered and receive later clicks.
- WHEN a click arrives while the app has no resumed Activity THEN the URL SHALL open from the application context with `FLAG_ACTIVITY_NEW_TASK`.
- WHEN the engine setup throws THEN `initialize` and push SHALL keep working; one error line with the exception class.
- WHEN a message is visible during rotation or Activity recreation THEN it SHALL be rebuilt with no second impression (RN).
- WHEN Back is pressed THEN center/full SHALL consume it with no dismiss and bars SHALL not intercept it (RN).

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| INAPP-01..08 | P1: Messages shown per session | Execute | Done (T1-T6) |
| INAPP-09..12 | P1: Host events and clicks | Execute | Done (T7, T9) |
| INAPP-13..18 | P1: URL actions | Execute | Done (T8, T9) |
| INAPP-19 | P1: Push-permission button | Execute | Done (T4, T5, T9) |
| INAPP-20 | P2: Documentation | Execute | Done (T11) |

**Coverage:** 20 total, 20 mapped to tasks and tests (`traceability.md`).

---

## Success Criteria

- [x] Ported In-App test counts equal RN per class (276 In-App + 9 push-permission), plus new facade/URL tests; total test count only grows.
- [x] Full gate green: spotless, lint, tests, `apiCheck`, publish, example against the published AAR, scripts (with the URL guard allowlist).
- [x] `scripts/port-diff.sh` shows only listed changes for each ported file.
