// Cloud sessions hit Maven Central rate limits (also behind the Gradle Plugin Portal,
// which redirects there). Google's mirror serves the same artifacts, so it goes first.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"

fun RepositoryHandler.mirrorFirst() {
    val repo = maven(mirror) { name = "CentralMirror" }
    remove(repo)
    addFirst(repo)
}

beforeSettings {
    pluginManagement.repositories.gradlePluginPortal()
    pluginManagement.repositories.mirrorFirst()
    dependencyResolutionManagement.repositories.mirrorFirst()
}
