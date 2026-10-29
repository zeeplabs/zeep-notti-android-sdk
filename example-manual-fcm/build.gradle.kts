// Build proof for hosts that own their FirebaseMessagingService (push-integration PUSH-15): the manifest
// removes the SDK's service and the app's own service forwards to NottiBridge. Not published.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.zeeplabs.notti.example.manualfcm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zeeplabs.notti.example.manualfcm"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The SDK version comes from :notti (NottiSdkVersion.CURRENT), never a literal here.
evaluationDependsOn(":notti")

dependencies {
    if (providers.gradleProperty("notti.usePublished").orNull == "true") {
        implementation("com.zeeplabs:notti-android:${project(":notti").version}")
    } else {
        implementation(project(":notti"))
    }
}
