import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.publish)
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

    // Warnings are errors on the library (PKG-10). The version-staleness checks are off: they fail the
    // build whenever upstream publishes, and versions are pinned on purpose (AD-012, AD-013).
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
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

// ABI guard (PKG-13, AD-010, AD-012). KGP's built-in ABI validation and BCV's auto-wiring both key off the
// `org.jetbrains.kotlin.android` plugin, which AGP 9 built-in Kotlin does not apply, so BCV's task types
// are wired by hand on the release classes. `apiDump` updates notti/api/notti.api; `apiCheck` runs in `check`.
val bcvRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    bcvRuntime(libs.asm)
    bcvRuntime(libs.asm.tree)
    bcvRuntime(libs.kotlin.metadata.jvm)
}

val apiBuild =
    tasks.register<KotlinApiBuildTask>("apiBuild") {
        inputClassesDirs.from(tasks.named<KotlinCompile>("compileReleaseKotlin").flatMap { it.destinationDirectory })
        outputApiFile.set(layout.buildDirectory.file("api/notti.api"))
        runtimeClasspath.from(bcvRuntime)
    }

tasks.register<Copy>("apiDump") {
    group = "verification"
    description = "Writes the public ABI of the release classes to api/notti.api."
    from(apiBuild.flatMap { it.outputApiFile })
    into(layout.projectDirectory.dir("api"))
}

val apiCheck =
    tasks.register<KotlinApiCompareTask>("apiCheck") {
        group = "verification"
        description = "Fails when the public ABI differs from api/notti.api."
        projectApiFile.set(layout.projectDirectory.file("api/notti.api"))
        generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
    }

tasks.named("check") { dependsOn(apiCheck) }

// Publication (PKG-02, AD-003, AD-004). `publishAllPublicationsToBuildRepository` writes to build/repo with no
// credentials; Maven Central publishing is done only at release time (example-and-release).
mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        ),
    )
    coordinates("com.zeeplabs", "notti-android", nottiVersion)

    // Central Portal upload without automatic release: Julio reviews the deployment and presses Publish
    // (AD-015). Credentials are read only by the upload task, so local and PR builds need none.
    publishToMavenCentral()

    pom {
        name.set("Notti Android SDK")
        description.set("Native Android SDK for Notti push notifications and In-App Messages.")
        url.set("https://github.com/zeeplabs/zeep-notti-android-sdk")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("zeeplabs")
                name.set("Zeep Tecnologia")
                url.set("https://github.com/zeeplabs")
            }
        }
        scm {
            url.set("https://github.com/zeeplabs/zeep-notti-android-sdk")
            connection.set("scm:git:git://github.com/zeeplabs/zeep-notti-android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/zeeplabs/zeep-notti-android-sdk.git")
        }
    }

    // Signed only when the release job provides the in-memory key; local and PR publishes stay unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

// Central rejects unsigned artifacts; fail in seconds and name the cause (D6). Attached to the plugin's
// prepare task, which every Central upload runs first: a doFirst on publishToMavenCentral (a lifecycle task)
// would run only after the upload.
val signingKeyPresent = providers.gradleProperty("signingInMemoryKey").map { it.isNotBlank() }.orElse(false)
tasks.matching { it.name == "prepareMavenCentralPublishing" }.configureEach {
    doFirst {
        if (!signingKeyPresent.get()) {
            throw GradleException("signingInMemoryKey is not set; refusing an unsigned upload")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "build"
            url = uri(rootProject.layout.buildDirectory.dir("repo"))
        }
    }
}
