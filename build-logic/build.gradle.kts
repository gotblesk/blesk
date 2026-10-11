plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.roborazzi.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "blesk.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "blesk.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "blesk.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "blesk.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
