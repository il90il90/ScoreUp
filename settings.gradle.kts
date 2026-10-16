pluginManagement {
    repositories {
        // Optional local mirror (see scripts/fetch-deps-mirror.sh) for offline builds.
        val mirror = file(".deps-mirror/m2")
        if (mirror.isDirectory) maven { url = mirror.toURI() }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val mirror = file(".deps-mirror/m2")
        if (mirror.isDirectory) maven { url = mirror.toURI() }
        google()
        mavenCentral()
    }
}
rootProject.name = "ScoreUp"
include(":core", ":adb", ":shared", ":app", ":wear")
