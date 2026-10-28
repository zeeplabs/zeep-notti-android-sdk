import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
}

// NottiSdkVersion.CURRENT is the only version literal (PKG-06): the publication reads it here, so
// the version sent as `sdk_version` and the Maven version cannot drift.
val nottiVersion: String =
    run {
        val source = file("src/main/java/com/zeeplabs/notti/NottiSdkVersion.kt")
        if (!source.isFile) throw GradleException("Missing ${source.path}")
        val matches =
            Regex("""const\s+val\s+CURRENT\s*=\s*"([^"]+)"""")
                .findAll(source.readText())
                .map { it.groupValues[1] }
                .toList()
        if (matches.size != 1) {
            throw GradleException("Expected exactly one NottiSdkVersion.CURRENT in ${source.path}, found ${matches.size}")
        }
        matches.single()
    }

group = "com.zeeplabs"
version = nottiVersion

android {
    namespace = "com.zeeplabs.notti"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Hosts on Kotlin 2.0+ can read the AAR's metadata (AD-012).
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    // `api`: NottiBridge exposes Firebase's RemoteMessage (push-integration).
    api(libs.firebase.messaging)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
}
