pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://build-artifacts.signal.org/libraries/maven/") {
            content { includeGroup("org.signal") }
        }
        // LiveKit's audio routing library is published only there.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.davidliu") }
        }
    }
}

rootProject.name = "blesk"

include(":app")
include(":shared")
include(":server")
include(":core:design")
include(":core:data")
include(":core:crypto")
include(":core:media")
include(":core:network")
include(":core:messaging")
include(":core:calls")
include(":feature:chats")
include(":feature:settings")
include(":feature:debug")
include(":feature:security")
include(":feature:profile")
include(":feature:calls")
