// Pure-JVM build of :core only (no Android Gradle Plugin): fast engine tests anywhere.
// Usage: gradle -p scripts/core-only :core:test
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "scoreup-core-only"
include(":core")
project(":core").projectDir = file("../../core")
