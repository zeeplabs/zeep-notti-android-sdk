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

    // Minified release (push-integration T8): CI checks R8 keeps the SDK's manifest components with no keep
    // rules in consumer-rules.pro. Signed with the debug key; this sample is never distributed.
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
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
