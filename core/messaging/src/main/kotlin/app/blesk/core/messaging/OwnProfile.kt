package app.blesk.core.messaging

import java.io.File

/** What goes out about you: your name, bio and links, and the file of your photo if there is one. */
data class OwnProfile(val name: String, val bio: String, val links: List<String> = emptyList(), val photo: File? = null) {
    val isEmpty: Boolean get() = name.isBlank() && bio.isBlank() && links.isEmpty() && photo == null
}
