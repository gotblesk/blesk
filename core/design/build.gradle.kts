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
    implementation(libs.androidx.core.ktx)
    implementation(libs.backdrop)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
