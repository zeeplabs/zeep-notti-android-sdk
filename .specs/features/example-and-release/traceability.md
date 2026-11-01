# Example App and Release 0.1.0 Traceability

**Spec**: `.specs/features/example-and-release/spec.md`
**Design**: `.specs/features/example-and-release/design.md`
**Status**: Execute done 2026-10-31 (T1-T11, local branch `feat/example-and-release`); awaiting review/PR. Release itself (tag, upload, Publish) is Julio's (AD-015).

No SDK code changed: `notti/src/` untouched, `notti/build.gradle.kts` changed only in T7. Unit tests stay at 718.

---

## Requirements

| ID | Requirement | Task(s) | Evidence |
| --- | --- | --- | --- |
| EXR-01 | Example builds with no local files | T1, T3 | Example gate with no `notti.properties` / `google-services.json`; CI `build` job (plain builds before the Firebase step) |
| EXR-02 | Example references every public member; `NottiBridge` in manual-fcm; guard + self-test | T3, T4 | `scripts/check-example-coverage.sh` (passes; removing `Notti.User.clearPhone()` failed naming it); `scripts/tests/check-example-coverage-test.sh` (12 cases); CI `checks` step |
| EXR-03 | Redacted screen and log | T3 | `ExampleEventLog` (only sink to logcat); grep of `ExampleEventLog.add` lines: call names, booleans, level, data keys, message/button ids, action type only |
| EXR-04 | Not configured: no `initialize`, visible state | T1, T3 | `ExampleConfig.isComplete`; `ExampleApplication` and `ManualFcmApplication` skip `initialize`; Status section shows `NOT_CONFIGURED`; checklist DC-02 |
| EXR-05 | Google Services plugin only when the module has the file | T1, T2 | Builds with and without the fixture (both modules, `google_app_id` asserted); CI step "Example apps with Firebase config (EXR-05)"; library never applies it |
| EXR-06 | Config git-ignored, sample tracked, guard | T1, T2 | `.gitignore` (`notti.properties`, `google-services.json`); `example/notti.properties.sample` (fixture values); `scripts/check-no-tracked-config.sh` + self-test (8 cases); CI `checks` step |
| EXR-07 | README sections in order, no draft | T5 | `README.md` headings; `grep -i draft` empty |
| EXR-08 | Data sent, `notti_prefs` keys, Data safety answers, LGPD note | T5 | README "Data the SDK handles"; field list traced in commit `2b71464` body |
| EXR-09 | Known limitations | T5 | README "Known limitations" (every EXR-09 item, plus backend version and tags after `logout`) |
| EXR-10 | Placeholders only; default host once | T5 | `grep -c app.zeepnotti.app README.md` = 1; `YOUR_APP_ID` / `example.invalid` only |
| EXR-11 | `opened` + `clicked`; `logout` wording | T5 | README "Listeners and the initial click", "Identity and tags" (wording follows `NottiCore.logout`: email and phone cleared on the device and the server) |
| EXR-12 | Device checklist with every listed item | T6 | `docs/DEVICE_CHECKLIST.md` DC-01..DC-25 |
| EXR-13 | Items cite todos/gaps; closing is post-checklist | T6 | "Source" line per item; "After the run" section |
| EXR-14 | CHANGELOG `## [0.1.0] - TBD`, banner | T10 | `CHANGELOG.md` (Added, Fixed, Known limitations, Migration); README banner |
| EXR-15 | `check-version.sh` | T10 | Passes; tag `0.1.0` fails only on `TBD`; dated copy passes; `v0.1.0` fails |
| EXR-16 | `release.yml` on tag, signed upload without release, pinned, no secret printed | T7, T8 | `.github/workflows/release.yml`; `publishToMavenCentral()`; guard on `prepareMavenCentralPublishing` (fails before upload, proven `--offline`); static review only (`actionlint` absent, YAML parses) |
| EXR-17 | Signed dry run | T7 | `scripts/check-release-artifacts.sh`: passed locally (5 signatures, 6 POM fields); CI `build` step |
| EXR-18 | Release runbook | T9 | `docs/RELEASING.md` |
| EXR-19 | Release PR lists the pre-publish gates | T11 | PR description (scratchpad `pr-example-release.md`) |
| EXR-20 | CI runs the new checks | T2, T4, T7 | `ci.yml`: `check-example-coverage.sh`, `check-no-tracked-config.sh` (checks), Firebase build and `check-release-artifacts.sh` (build) |

**Coverage:** 20/20 mapped. Not proven on CI (B-002): every CI change was run locally as written.

---

## Deviations from design

| Item | Design | Implemented | Why |
| --- | --- | --- | --- |
| Firebase fixture | one client | one client per example package | each module's `google_app_id` asserted separately |
| CI Firebase step | `:example` only | both example modules | same proof for manual-fcm |
| manual-fcm config | `ExampleConfig` | inline check in `ManualFcmApplication` | the module cannot see `:example` sources |
| Status section | SDK version | none | `NottiSdkVersion` is internal |
| Device switches | switches with state | check boxes, start unchecked | the SDK has no getters |
| Coverage guard | member name only | getter/setter name also covers a property | Java sources |
| Coverage fixtures | `scripts/tests/fixtures/` | built in the self-test's temp dirs | same pattern as the other self-tests |
| Unsigned-upload guard | `doFirst` on `publishToMavenCentral` | `doFirst` on `prepareMavenCentralPublishing` | the lifecycle task's actions run after the upload |
| Release concurrency | `release-<ref>` | `release-upload-<ref>` | groups are case-insensitive; the called CI uses `Release-<ref>` |
| `logout` wording | email and phone cleared locally | cleared on the device and the server | matches `NottiCore.logout` |

## Open after this feature

- Device checklist run (DC-01..DC-27; DC-26 and DC-27 added by pre-release-review), then close the cited todos (EXR-13).
- Play Data safety answers confirmed by Julio (AD-011).
- B-001 (namespace) and B-002 (CI billing).
- Proposed ADs to confirm: AD-005, AD-008..AD-011.
