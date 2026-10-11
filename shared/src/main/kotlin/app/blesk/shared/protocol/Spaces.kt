package app.blesk.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Public spaces: channels and open rooms. They are NOT end-to-end encrypted — the server
 * stores their posts in the clear — and the app always shows them matte and labelled.
 * A channel has one stream where only admins post; an open room has several text channels
 * where every member posts.
 */
object Spaces {
    const val PATH = "/v1/spaces"

    /** 3–32 lowercase latin letters, digits and underscores, like usernames. */
    private val handlePattern = Regex("^[a-z0-9_]{3,32}$")

    fun isValidHandle(handle: String) = handlePattern.matches(handle)

    const val MAX_TITLE = 64
    const val MAX_ABOUT = 280
    const val MAX_POST = 4096
    const val MAX_CHANNELS = 20
}

@Serializable
enum class SpaceKind {
    @SerialName("channel")
    Channel,

    @SerialName("room")
    Room,
}

@Serializable
enum class SpaceRole {
    @SerialName("owner")
    Owner,

    @SerialName("admin")
    Admin,

    @SerialName("member")
    Member,
}

@Serializable
data class SpaceChannel(val id: String, val title: String)

@Serializable
data class CreateSpace(
    val handle: String,
    val kind: SpaceKind,
    val title: String,
    val about: String = "",
)

@Serializable
data class UpdateSpace(val title: String? = null, val about: String? = null)

/** What anyone can see about a space. [role] is the caller's role, null if not a member. */
@Serializable
data class SpaceInfo(
    val handle: String,
    val kind: SpaceKind,
    val title: String,
    val about: String,
    val members: Int,
    val channels: List<SpaceChannel>,
    val role: SpaceRole? = null,
)

@Serializable
data class CreateSpaceChannel(val title: String)

@Serializable
data class NewPost(
    /** Chosen by the author; repeating it is ignored, which makes retries safe. */
    val clientId: String,
    val text: String,
    /** For rooms: which text channel. Channels have a single stream with id "main". */
    val channel: String = MAIN_CHANNEL,
    val replyTo: Long? = null,
    /** An attachment uploaded through /v1/attachments, unencrypted: [AttachmentPointer.key] stays empty. */
    val attachment: AttachmentPointer? = null,
) {
    companion object {
        const val MAIN_CHANNEL = "main"
    }
}

@Serializable
data class Post(
    val id: Long,
    val space: String,
    val channel: String,
    val author: String,
    val text: String,
    val createdAtMillis: Long,
    val replyTo: Long? = null,
    val attachment: AttachmentPointer? = null,
    val editedAtMillis: Long? = null,
)

@Serializable
data class EditPost(val text: String)

@Serializable
data class RoleChange(val username: String, val role: SpaceRole)

@Serializable
data class SpaceMember(val username: String, val role: SpaceRole)
