plugins {
    alias(libs.plugins.blesk.android.library)
}

android {
    namespace = "com.twilio.audioswitch"
    buildFeatures.buildConfig = true
    defaultConfig.buildConfigField("String", "VERSION_NAME", "\"039a35a\"")
}

// Third-party code kept exactly as published: our style rules do not apply to it.
tasks.matching { it.name.startsWith("detekt") }.configureEach { enabled = false }

dependencies {
    implementation(libs.androidx.annotation.legacy)
}
