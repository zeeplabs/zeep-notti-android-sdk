package com.zeeplabs.notti

/**
 * The SDK version: the only place it is defined (PKG-06). `notti/build.gradle.kts` reads this
 * literal for the Maven publication and `scripts/check-version.sh` compares it with the git tag
 * and the CHANGELOG. Sent to the backend as `sdk_version`.
 */
internal object NottiSdkVersion {
    const val CURRENT = "0.1.0"
}
