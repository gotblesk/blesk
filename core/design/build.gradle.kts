plugins {
    alias(libs.plugins.blesk.android.library)
    alias(libs.plugins.blesk.android.compose)
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.kotlinx.collections.immutable)
    api(project(":shared"))
    api(project(":core:motion"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.backdrop)
    implementation(libs.zxing.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
}
