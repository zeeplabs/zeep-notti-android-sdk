pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // -Pnotti.usePublished=true: :example resolves the SDK from the local Maven repository that
        // :notti:publishAllPublicationsToBuildRepository fills, so CI tests the AAR and the POM (PKG-03).
        if (providers.gradleProperty("notti.usePublished").orNull == "true") {
            maven { url = uri(layout.rootDirectory.dir("build/repo")) }
        }
    }
}

rootProject.name = "zeep-notti-android-sdk"
include(":notti", ":example")
