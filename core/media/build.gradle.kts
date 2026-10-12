plugins {
    alias(libs.plugins.blesk.android.library)
}

dependencies {
    api(project(":core:data"))
    implementation(project(":core:crypto"))
    api(libs.kotlinx.coroutines.android)
    api(libs.media3.exoplayer)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    api(libs.camerax.core)
    implementation(libs.camerax.camera2)
    api(libs.camerax.lifecycle)
    api(libs.camerax.video)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
}
