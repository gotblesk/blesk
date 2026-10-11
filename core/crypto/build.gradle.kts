plugins {
    alias(libs.plugins.blesk.android.library)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
}
