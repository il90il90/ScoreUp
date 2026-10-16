import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signingInfo = ScoreUpBuild.signing(project)

android {
    namespace = "com.scoreup.app"
    compileSdk = 35
    defaultConfig {
        // Phone and watch MUST share applicationId + signing key for the Wear Data Layer.
        applicationId = "com.scoreup.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ScoreUpBuild.versionCode(project)
        versionName = ScoreUpBuild.versionName(project)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Google Cast receiver: default = our custom web receiver ID if configured, else Default Media Receiver.
        buildConfigField("String", "CAST_RECEIVER_APP_ID", "\"${System.getenv("SCOREUP_CAST_APP_ID") ?: "CC1AD845"}\"")
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("scoreup")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true }
        animationsDisabled = true
    }
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    androidResources { noCompress += "apk" }
}

/** Embeds the matching :wear APK into the phone app's assets so the phone can install it on a watch. */
abstract class EmbedWearApk : DefaultTask() {
    @get:InputFiles abstract val apks: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively(); out.mkdirs()
        val apk = apks.asFileTree.matching { include("**/*.apk") }.files.maxByOrNull { it.lastModified() }
            ?: error("No wear APK found in ${apks.files}")
        apk.copyTo(File(out, "wear/scoreup-wear.apk"), overwrite = true)
    }
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    val cap = variant.name.replaceFirstChar { it.uppercase() }
    val embed = tasks.register<EmbedWearApk>("embedWearApk$cap") {
        dependsOn(":wear:assemble$cap")
        apks.from(project(":wear").layout.buildDirectory.dir("outputs/apk/${variant.name}"))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(embed, EmbedWearApk::outputDir)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.play.cast.framework)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.appcompat)
    implementation(project(":adb"))
    implementation(libs.conscrypt)
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
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.truth)
}

// Lets Robolectric fetch its Android runtime jars from a Maven Central mirror when set.
tasks.withType<Test>().configureEach {
    System.getenv("ROBOLECTRIC_REPO")?.let { systemProperty("robolectric.dependency.repo.url", it) }
}
