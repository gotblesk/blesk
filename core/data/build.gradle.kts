plugins {
    alias(libs.plugins.blesk.android.library)
}

dependencies {
    api(project(":shared"))
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.turbine)
}
