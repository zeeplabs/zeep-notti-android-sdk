buildscript {
    dependencies {
        // Pins the Kotlin compiler used by AGP built-in Kotlin; AGP 9.2.1 alone brings KGP 2.2.10 (AD-012).
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    // Only the BCV task classes are used; :notti wires them by hand (AD-012).
    alias(libs.plugins.kotlinx.bcv) apply false
}
