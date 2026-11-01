# Pre-release Review Fixes Specification

**Source:** independent pre-release review of `main` at `4dcaf5d` (2026-11-01, findings F1-F10). Julio approved fixing all ten before tagging 0.1.0 (2026-11-01).
**Scope:** Medium. Fixes before the first immutable release; no new feature. Behavior changes to ported code (PRR-06, PRR-07, PRR-10) are approved exceptions to AD-002, recorded in AD-019; the RN repo gets the same fixes in M2 or its own spec.

## Problem

The review found no blocker beyond the tracked pre-release items, but several things are cheap to fix now and expensive after `0.1.0` is on Maven Central: the POM and AAR metadata force upgrades on hosts (Kotlin stdlib, `compileSdk`), the tag-build guard does not check what the runbook says it checks, the public API has an evolution trap and throws on Java `null`, and three lifecycle edges can misbehave on a device.

## Requirements

| ID | Finding | Requirement | Verification |
| --- | --- | --- | --- |
| PRR-01 | F1 | The published POM depends on `kotlin-stdlib` 2.0.x (not 2.2.x), so a host compiling with Kotlin 2.0 can use the SDK. README Requirements state the Kotlin floor. CI fails if the POM's stdlib is not 2.0.x. | POM inspection in CI step "AAR and POM contents" |
| PRR-02 | F2 | On a tag build, `scripts/check-version.sh` fails while the README still contains the "Until the release is published" sentence; the self-test covers that sentence. `docs/RELEASING.md` describes what the script checks. | `scripts/tests/check-version-test.sh` |
| PRR-03 | F3 | The AAR declares `minCompileSdk` 34 (the floor its own dependencies already set: `core-ktx` 1.13.1). README Requirements say 34. CI fails on any other value. | AAR `aar-metadata.properties` check in CI |
| PRR-04 | F4 | `NottiOptions` KDoc states the real evolution rule (keep the old constructor as a hidden overload when adding a parameter). The README `when` example over `NottiInAppAction.Type` has an `else` branch and the README says public enums may gain values in a minor release. AD-018 records the rule. | review; `apiCheck` |
| PRR-05 | F5 | A Java caller passing `null` to a `String`, `Map` or `List` parameter of `Notti`, `Notti.User` or `Notti.trackEvent`/`initialize` (app id, client key) does not throw: the call is logged (method name only) and ignored; `null` keys or values inside a `Map`/`List` are dropped. Kotlin callers' source and the JVM signatures do not change. | `NottiJavaNullTest`; `apiCheck` unchanged |
| PRR-06 | F6 | The SDK's own `NottiPermissionActivity` never becomes the current Activity of the runtime nor the resumed Activity of the In-App tracker, and its resume never runs tap detection. | Robolectric tests |
| PRR-07 | F7 | `requestPermission` (and the In-App `push_permission` prompt) shows the dialog only while the app is in the foreground (process lifecycle at least `STARTED`); otherwise it answers `false` and does not set `notti_permission_requested`. | `NottiPermissionRequesterTest` |
| PRR-08 | F8 | README Logging names the one accepted URL line (an invalid `baseUrl` is logged to explain why `initialize` was ignored). README Identity and "Removing the process-start hook" say that without the provider every call before `initialize` is dropped with a `WARN` line. | review |
| PRR-09 | F9 | `docs/RELEASING.md` makes the `maven-central` environment's required reviewer and tag-only deployment rule mandatory setup steps. Every `actions/checkout` sets `persist-credentials: false`. | review; workflow YAML parse |
| PRR-10 | F10 | Tap detection in `NottiActivityLifecycleListener` never throws into the host: an exception while reading an Intent's extras is caught and logged by class. | Robolectric test with a throwing Intent |

## Out of scope

- Running the device checklist, Data safety confirmation, B-001, CHANGELOG date and README banner removal (release PR).
- Porting PRR-06/07/10 to `react-native-notti` (M2).
- A `Notti` builder API or nullable listener parameters (listener and callback arguments stay non-null; README says so).
