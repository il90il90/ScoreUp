plugins { `kotlin-dsl` }
repositories {
    val mirror = file("../.deps-mirror/m2")
    if (mirror.isDirectory) maven { url = mirror.toURI() }
    mavenCentral()
    gradlePluginPortal()
}
