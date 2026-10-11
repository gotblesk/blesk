plugins {
    alias(libs.plugins.blesk.android.library)
}

dependencies {
    api(project(":core:data"))
    api(libs.kotlinx.coroutines.android)
    api(libs.livekit.android)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
}
