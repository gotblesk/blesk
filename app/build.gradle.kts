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
        versionName = "0.2.0" + (providers.gradleProperty("blesk.versionSuffix").orNull ?: "")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
}
