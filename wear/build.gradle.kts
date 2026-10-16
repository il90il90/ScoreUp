plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signingInfo = ScoreUpBuild.signing(project)

android {
    namespace = "com.scoreup.wear"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.scoreup.app"
        minSdk = 30
        targetSdk = 34
        versionCode = ScoreUpBuild.versionCode(project)
        versionName = ScoreUpBuild.versionName(project)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("scoreup") {
            storeFile = signingInfo.file
            storePassword = signingInfo.storePassword
            keyAlias = signingInfo.alias
            keyPassword = signingInfo.keyPassword
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("scoreup") }
        release {
            // -Pscoreup.minify=false builds an unshrunk release (same code paths as the tested debug build).
            val minify = (project.findProperty("scoreup.minify") as String?)?.toBoolean() ?: true
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("scoreup")
        }
    }
    // Lint runs as its own CI step; release packaging must not depend on lint jars.
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true }
        animationsDisabled = true
    }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.truth)
}

// Lets Robolectric fetch its Android runtime jars from a Maven Central mirror when set.
tasks.withType<Test>().configureEach {
    System.getenv("ROBOLECTRIC_REPO")?.let { systemProperty("robolectric.dependency.repo.url", it) }
}
