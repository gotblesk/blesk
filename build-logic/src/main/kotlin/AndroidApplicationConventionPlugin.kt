import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            // libsignal-android uses java.time and friends beyond what minSdk 26 ships.
            compileOptions.isCoreLibraryDesugaringEnabled = true
            testOptions.unitTests.isIncludeAndroidResources = true
            testOptions.unitTests.isReturnDefaultValues = true
        }
        configureDetekt()
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
        }
    }
}
