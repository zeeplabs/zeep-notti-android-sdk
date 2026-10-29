plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.zeeplabs.notti.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zeeplabs.notti.example"
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
    implementation(libs.androidx.activity)
    if (providers.gradleProperty("notti.usePublished").orNull == "true") {
        // CI: resolve the published AAR and POM from build/repo (PKG-03). Run the publish task in a
        // separate Gradle invocation first.
        implementation("com.zeeplabs:notti-android:${project(":notti").version}")
    } else {
        implementation(project(":notti"))
    }
}
