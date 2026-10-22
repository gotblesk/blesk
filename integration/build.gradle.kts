plugins {
    alias(libs.plugins.blesk.android.library)
}

dependencies {
    testImplementation(project(":core:messaging"))
    testImplementation(libs.libsignal.client)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
}

tasks.withType<Test>().configureEach {
    // The server lives outside Gradle: scripts/ci/integration.sh starts it and says where it is.
    val server = listOf("BLESK_IT_URL", "BLESK_IT_PIN", "BLESK_IT_INVITES").map { providers.environmentVariable(it) }
    onlyIf("a test server is running (scripts/ci/integration.sh)") { server.all { it.isPresent } }
    // Its answers come from a server Gradle knows nothing about, so a previous pass proves nothing.
    outputs.upToDateWhen { false }
}
