plugins {
    alias(libs.plugins.blesk.android.library)
    alias(libs.plugins.blesk.android.compose)
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:calls"))
    implementation(libs.androidx.lifecycle.runtime.compose)
}
