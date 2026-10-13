import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun Project.configureKotlinAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlinCompiler()
    configureUnitTests()
}

/** Robolectric reaches into JDK internals that JDK 17+ closes by default. */
internal fun Project.configureUnitTests() {
    tasks.withType(Test::class.java).configureEach {
        jvmArgs(
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "-Xmx2g",
        )
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
}

internal fun Project.configureKotlinCompiler() {
    tasks.withType(KotlinJvmCompile::class.java).configureEach {
        compilerOptions.configureBlesk()
    }
}

private fun KotlinJvmCompilerOptions.configureBlesk() {
    jvmTarget.set(JvmTarget.JVM_17)
    allWarningsAsErrors.set(false)
    freeCompilerArgs.add("-Xjsr305=strict")
}

internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/main/java")
    }
    dependencies.add("detektPlugins", libs.findLibrary("detekt-formatting").get())
    // blesk's own rules, such as a tap target that does not answer the finger.
    dependencies.add("detektPlugins", dependencies.project(mapOf("path" to ":detekt-rules")))
}

