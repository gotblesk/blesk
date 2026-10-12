plugins {
    alias(libs.plugins.blesk.android.application)
    alias(libs.plugins.blesk.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseKey: String? = providers.environmentVariable("BLESK_KEYSTORE_FILE").orNull

/** Puts `docs/RELEASE_NOTES.md` into the app's assets for «Что нового». */
abstract class ReleaseNotesTask : DefaultTask() {
    @get:InputFile
    abstract val notes: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        notes.get().asFile.copyTo(outputDir.get().file("RELEASE_NOTES.md").asFile, overwrite = true)
    }
}

val releaseNotes = tasks.register<ReleaseNotesTask>("releaseNotes") {
    notes.set(rootProject.layout.projectDirectory.file("docs/RELEASE_NOTES.md"))
    outputDir.set(layout.buildDirectory.dir("generated/releaseNotes"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(releaseNotes, ReleaseNotesTask::outputDir) }
}

android {
    namespace = "app.blesk"

    defaultConfig {
        applicationId = "app.blesk"
        // CI passes the run number so every build installs over the previous one.
        versionCode = providers.gradleProperty("blesk.versionCode").orNull?.toInt() ?: 1
        versionName = "1.0.0-rc2" + (providers.gradleProperty("blesk.versionSuffix").orNull ?: "")
    }

    signingConfigs {
        // Test-only key kept in the repository so CI builds update over each other.
        create("test") {
            storeFile = rootProject.file("signing/blesk-test.jks")
            storePassword = "blesk-test-only"
            keyAlias = "blesk-test"
            keyPassword = "blesk-test-only"
        }
        // The real key lives only in the CI secrets; built-in updates trust nothing else once it is used.
        releaseKey?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = providers.environmentVariable("BLESK_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("BLESK_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("BLESK_KEY_PASSWORD").orNull
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        ndk {
            // Phones and the x86_64 emulator; 32-bit x86 devices do not exist in practice.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Needed to strip debug symbols from native libraries; without it libsignal alone is hundreds of megabytes.
    ndkVersion = "30.0.16248370"

    packaging {
        jniLibs {
            // libsignal-android also ships a testing build of its native library.
            excludes += listOf("**/libsignal_jni_testing.so")
        }
        resources {
            // libsignal-client also ships desktop natives for JVM use; they have no place in an APK.
            excludes += listOf("libsignal_jni*.so", "libsignal_jni*.dylib", "signal_jni*.dll")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (releaseKey != null) "release" else "test")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":feature:chats"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:debug"))
    implementation(project(":feature:security"))
    implementation(project(":feature:profile"))
    implementation(project(":core:crypto"))
    implementation(project(":core:media"))
    implementation(project(":core:messaging"))
    implementation(project(":core:calls"))
    implementation(project(":feature:calls"))
    implementation(libs.androidx.core.telecom)
    implementation(libs.camerax.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.session)
}
