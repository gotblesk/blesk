plugins {
    alias(libs.plugins.blesk.android.library)
    alias(libs.plugins.blesk.android.compose)
}

android {
    // Strings of every module share one namespace, so a name here must never stand for another module's text.
    resourcePrefix = "onboarding_"
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
}
