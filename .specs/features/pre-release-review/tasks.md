# Pre-release Review Fixes Tasks

**Spec**: `.specs/features/pre-release-review/spec.md`
**Testing**: `.specs/codebase/TESTING.md`
**Status**: In progress (2026-11-01)

---

## Execution Protocol

`tlc-spec-driven` Execute flow; rules in `AGENTS.md`. One task per commit, tests in the same commit, test count never drops. Push, PR, tag and upload only with Julio's OK (AD-015).

## Gate

Full (every task that touches `notti/` or the build): `./gradlew spotlessCheck :notti:lintRelease :notti:testDebugUnitTest :notti:apiCheck :notti:assembleRelease :notti:publishAllPublicationsToBuildRepository`, then `./gradlew -Pnotti.usePublished=true :example:assembleDebug :example:assembleRelease :example-manual-fcm:assembleDebug`, then `scripts/check-version.sh && scripts/check-package-name.sh && scripts/check-no-url-open.sh && scripts/check-example-coverage.sh && scripts/check-no-tracked-config.sh && scripts/tests/run.sh`.

## Tasks

- [x] **T1** `docs(specs)`: this spec, tasks, AD-017..AD-019 in `STATE.md`.
- [x] **T2** PRR-02: `check-version.sh` banner pattern, self-test case, `RELEASING.md` wording.
- [x] **T3** PRR-01, PRR-03: `kotlin.stdlib.default.dependency=false` + `kotlin-stdlib` 2.0.21; `aarMetadata.minCompileSdk = 34`; CI assertions; README Requirements and Known limitations.
- [ ] **T4** PRR-05: Java `null` tolerance in the facade; `NottiJavaNullTest`; README Java section.
- [ ] **T5** PRR-04: `NottiOptions` KDoc; README `when` example and enum note.
- [ ] **T6** PRR-06: trackers skip `NottiPermissionActivity`; tests; device checklist item.
- [ ] **T7** PRR-07: foreground check before the permission dialog; tests; README Notification permission.
- [ ] **T8** PRR-10: guarded tap detection; test.
- [ ] **T9** PRR-08: README Logging and before-`initialize` wording.
- [ ] **T10** PRR-09: `persist-credentials: false`; `RELEASING.md` environment rules mandatory.
- [ ] **T11** `docs(specs)`: traceability, STATE, ROADMAP; tasks marked done.
