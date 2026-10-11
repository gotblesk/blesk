plugins {
    alias(libs.plugins.blesk.android.library)
    alias(libs.plugins.blesk.android.compose)
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(libs.coil.compose)
    implementation(libs.media3.ui.compose)
    implementation(libs.camerax.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
}
