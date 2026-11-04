package com.zeeplabs.notti

/**
 * Marks API meant only for wrapper SDKs built on this one, such as `react-native-notti` (AD-022).
 *
 * Native apps must not use it: call [Notti.initialize] instead. Declarations with this mark are not
 * covered by semantic versioning and may change or disappear in any release. Kotlin callers must opt in
 * with `@OptIn(NottiWrapperApi::class)`; Java callers are not checked by the compiler.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message =
        "This API is for wrapper SDKs such as react-native-notti only; not covered by semver. " +
            "Native apps call Notti.initialize.",
)
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class NottiWrapperApi
