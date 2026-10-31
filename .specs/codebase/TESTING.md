# Testing

## Infrastructure

- JUnit 4.13.2 on the JVM (`notti/src/test/java/com/zeeplabs/notti/`).
- Robolectric 4.14.1 for tests that need framework classes (`Bundle`, `RemoteMessage`, Views); annotate the class with `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [34])` (or the SDK the ported RN test uses). `unitTests.isIncludeAndroidResources = true` is set.
- Real `org.json` (20240303) on the test classpath: the android.jar stub throws "not mocked" on the plain JVM.
- OkHttp `MockWebServer` and `okhttp-tls` 4.12.0 for HTTP and HTTPS tests.
- Proved by the smoke tests `RobolectricSmokeTest` and `OrgJsonSmokeTest` (PKG-11).
- Script guards have self-tests in `scripts/tests/` with temp fixture dirs; `scripts/tests/run.sh` runs them all.

## Commands

JDK 17 (`export JAVA_HOME=/opt/homebrew/opt/openjdk@17` on macOS with Homebrew).

| What | Command |
| --- | --- |
| All unit tests | `./gradlew :notti:testDebugUnitTest` |
| One class | `./gradlew :notti:testDebugUnitTest --tests 'com.zeeplabs.notti.<Class>'` |
| Style | `./gradlew spotlessCheck` (fix: `spotlessApply`) |
| Lint (warnings are errors) | `./gradlew :notti:lintRelease` |
| Public ABI | `./gradlew :notti:apiCheck` (update: `:notti:apiDump`) |
| Script guards | `scripts/check-version.sh && scripts/check-package-name.sh && scripts/check-no-url-open.sh && scripts/check-example-coverage.sh && scripts/check-no-tracked-config.sh && scripts/tests/run.sh && scripts/check-release-artifacts.sh` |

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Library code (`com.zeeplabs.notti`) | unit | every behavior; ported RN tests 1:1 with their requirement IDs | `notti/src/test/java/com/zeeplabs/notti/**/*Test.kt` | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` |
| Framework-dependent code (Bundle, RemoteMessage, Activity, Views) | unit (Robolectric) | same as above | same | same |
| Gradle build, publication, ABI, lint | none (build) + negative proof | build passes; each guard proved to fail once | - | Build gate |
| Scripts (`scripts/*.sh`) | script self-test | pass fixture + each failure mode | `scripts/tests/*-test.sh` | `scripts/tests/run.sh` |
| CI workflow | none | `actionlint` clean; red and green runs | - | `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest -no-color` |

Coverage (2026-10-28, `core-port`): 318 unit tests. 257 ported 1:1 from RN v0.8.0 (eight classes, counts equal to RN), 58 new (providers, runtime, continuity fixture, log privacy sentinel, RN validation gaps, logger), 3 smoke. RN requirement ID → test map, counts per class and the deferred list: `.specs/features/core-port/traceability.md`. Ported test files keep RN's names and `@Config`; ktlint findings on them are suppressed per file, not reformatted.

Coverage (2026-10-30, `public-api`): 444 unit tests (390 after `push-integration`, +54). New: facade (`NottiFacadeTest`, `NottiUserFacadeTest`, `NottiNotificationsFacadeTest`, `NottiPermissionFacadeTest`), `NottiListenersTest`, `NottiPublicTypesTest`, `NottiProcessHooksTest`, the first Java test (`NottiJavaInteropTest.java`, compiled by `compileDebugUnitTestJavaWithJavac`), plus runtime, lifecycle-listener and log-privacy cases. Map: `.specs/features/public-api/traceability.md`.

Coverage (2026-10-31, `in-app-messages`): 718 unit tests (+274). 236 ported 1:1 from RN v0.8.0 in eleven classes (In-App session, display clock, style, image loader, coordinator, presenter + lifecycle, view factory, push-permission action + settings intent, end-to-end), counts equal to RN. New: `NottiInAppClicksTest`, `NottiInAppUrlOpenerTest`, `NottiInAppCoordinatorUrlTest` (the coordinator's URL change, kept out of the ported class), `NottiInAppFacadeTest` (RN `NottiModuleTest` In-App cases), plus listener, public-type, process-hook, runtime and log-privacy cases. The URL guard has self-tests for its real allowlist. Map: `.specs/features/in-app-messages/traceability.md`.

## Parallelism Assessment

| Test Type | Parallel-Safe | Why |
| --- | --- | --- |
| Gradle (unit, Robolectric, build) | No | one checkout, one Gradle daemon and build dir; use a separate worktree for parallel work |
| Script self-tests | Yes | temp fixture dirs, no Gradle |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | tasks with unit tests | `./gradlew :notti:testDebugUnitTest --tests '<Class>'` |
| Build | Gradle config tasks | `./gradlew :notti:assembleRelease` |
| Full | end of a feature, before a PR | `./gradlew spotlessCheck :notti:lintRelease :notti:testDebugUnitTest :notti:apiCheck :notti:assembleRelease :notti:publishAllPublicationsToBuildRepository && ./gradlew -Pnotti.usePublished=true :example:assembleDebug && scripts/check-version.sh && scripts/check-package-name.sh && scripts/check-no-url-open.sh && scripts/check-example-coverage.sh && scripts/check-no-tracked-config.sh && scripts/tests/run.sh && scripts/check-release-artifacts.sh` |

`scripts/check-release-artifacts.sh` needs `gpg` (macOS: `brew install gnupg`, also needed for the real release key, see `docs/RELEASING.md`). Without it the script exits 2 with an install hint: report it as not run, CI runs it on every build.

The CI `build` job also checks the AAR (manifest, `proguard.txt`, no `com/notti/` class) and the POM (coordinates, no React Native).
