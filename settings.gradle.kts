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
    }
}

rootProject.name = "blesk"

include(":app")
include(":shared")
include(":core:design")
include(":core:data")
include(":feature:chats")
include(":feature:settings")
include(":feature:debug")
