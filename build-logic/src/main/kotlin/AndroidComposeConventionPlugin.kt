import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose on top of an Android module, plus Robolectric-backed screenshot tests:
 * every Compose module can record and verify Roborazzi snapshots.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("io.github.takahirom.roborazzi")
        val android = extensions.getByName("android") as CommonExtension
        android.buildFeatures.compose = true

        dependencies {
            val bom = platform(libs.findLibrary("compose-bom").get())
            add("implementation", bom)
            add("testImplementation", bom)
            add("implementation", libs.findLibrary("compose-foundation").get())
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("androidx-test-junit").get())
            add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
            add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
            add("testImplementation", libs.findLibrary("roborazzi").get())
            add("testImplementation", libs.findLibrary("roborazzi-compose").get())
            add("testImplementation", libs.findLibrary("roborazzi-junit-rule").get())
        }
    }
}
