plugins {
    alias(libs.plugins.blesk.android.application)
    alias(libs.plugins.blesk.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.blesk"

    defaultConfig {
        applicationId = "app.blesk"
        // CI passes the run number so every build installs over the previous one.
        versionCode = providers.gradleProperty("blesk.versionCode").orNull?.toInt() ?: 1
        versionName = "0.9.0" + (providers.gradleProperty("blesk.versionSuffix").orNull ?: "")
    }

    signingConfigs {
        // Test-only key kept in the repository so CI builds update over each other.
        create("test") {
            storeFile = rootProject.file("signing/blesk-test.jks")
            storePassword = "blesk-test-only"
            keyAlias = "blesk-test"
            keyPassword = "blesk-test-only"
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
            signingConfig = signingConfigs.getByName("test")
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
}
