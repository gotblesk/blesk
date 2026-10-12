plugins {
    alias(libs.plugins.blesk.android.library)
    alias(libs.plugins.blesk.android.compose)
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.zxing.core)
}
