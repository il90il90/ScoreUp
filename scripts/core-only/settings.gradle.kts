// Pure-JVM build of :core and :adb (no Android Gradle Plugin): fast engine tests anywhere.
// Usage: gradle -p scripts/core-only :core:test :adb:test
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "scoreup-core-only"
include(":core", ":adb")
project(":core").projectDir = file("../../core")
project(":adb").projectDir = file("../../adb")
