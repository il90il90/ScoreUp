plugins {
    alias(libs.plugins.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    // TLS keying-material exporter for the JVM tests (the app uses conscrypt-android).
    testImplementation(libs.conscrypt.openjdk)
}
