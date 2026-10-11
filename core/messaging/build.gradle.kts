plugins {
    alias(libs.plugins.blesk.android.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:data"))
    api(project(":core:crypto"))
    api(project(":core:network"))
    api(project(":core:media"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.libsignal.client)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
