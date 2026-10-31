import java.util.Properties

// Build proof for hosts that own their FirebaseMessagingService (push-integration PUSH-15): the manifest
// removes the SDK's service and the app's own service forwards to NottiBridge. Not published.
plugins {
    alias(libs.plugins.android.application)
}

// Local Notti config (AD-016): example/notti.properties is git-ignored; copy notti.properties.sample.
// Missing file or keys give empty strings, and the app then skips Notti.initialize.
val nottiProperties =
    Properties().apply {
        val file = rootProject.file("example/notti.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun nottiConfigField(key: String): String {
    val value = nottiProperties.getProperty(key).orEmpty().trim()
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

// Firebase config is the integrator's own file, never committed; without it the app still builds.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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
        buildConfigField("String", "NOTTI_APP_ID", nottiConfigField("notti.appId"))
        buildConfigField("String", "NOTTI_CLIENT_KEY", nottiConfigField("notti.clientKey"))
        buildConfigField("String", "NOTTI_BASE_URL", nottiConfigField("notti.baseUrl"))
    }

    buildFeatures {
        buildConfig = true
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
