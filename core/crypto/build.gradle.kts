plugins {
    alias(libs.plugins.blesk.android.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:data"))
    api(libs.libsignal.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bouncycastle.prov)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
    // Desktop natives of libsignal so the protocol can be exercised in plain JVM unit tests.
    testImplementation(libs.libsignal.client)
}
