# Pre-release Review Fixes Traceability

| ID | Finding | Task | Commit | Proof |
| --- | --- | --- | --- | --- |
| PRR-01 | F1 stdlib 2.2.21 in the POM | T3 | `5f0d6ec` | POM `kotlin-stdlib` 2.0.21 (no 2.2.x in POM or `.module`); CI "AAR and POM contents" asserts 2.0.x |
| PRR-02 | F2 banner guard pattern | T2 | `0f38a31` | `check-version-test.sh` unpublished-banner cases; current README fails on a tag build once the CHANGELOG is dated, passes after the sentence is removed |
| PRR-03 | F3 `minCompileSdk` 36 | T3 | `5f0d6ec` | AAR `aar-metadata.properties` `minCompileSdk=34`; CI asserts it |
| PRR-04 | F4 evolution rule, enums | T5 | `d697421` | KDoc, README `else` branch; AD-018 |
| PRR-05 | F5 Java `null` throws | T4 | `3645b60` | `NottiJavaNullTest` (NPE on the old facade), `NottiUserFacadeTest` null cases; `apiCheck` unchanged |
| PRR-06 | F6 permission Activity as host | T6 | `efd2699` | `NottiCurrentActivityTest`, `NottiInAppPresenterTest`; device DC-26 |
| PRR-07 | F7 background permission request | T7 | `87827e9` | `NottiPermissionRequesterTest` background case; device DC-27 |
| PRR-08 | F8 README vs code | T9 | `a4ba905` | review |
| PRR-09 | F9 release environment, checkout credentials | T10 | `f787732` | workflow YAML parse; runbook review |
| PRR-10 | F10 crafted Intent crash | T8 | `f722362` | `NottiTapDetectionGuardTest` |

Unit tests: 718 before, 725 after (0 failures).

## Gaps

- PRR-01 not proven against a real Kotlin 2.0 consumer build (no Kotlin 2.0 toolchain installed); the POM no longer carries a stdlib newer than 2.0.21 and class metadata stays 2.0 (AD-012 S1).
- PRR-06, PRR-07 device behavior: DC-26, DC-27.
- Same fixes in `react-native-notti` (PRR-06 does not apply there; PRR-07 and PRR-10 do): M2 or its own spec.
