package app.blesk.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Frames from the server on the WebSocket. */
@Serializable
sealed interface ServerFrame {
    /** A message waiting for this account. It stays queued until acknowledged. */
    @Serializable
    @SerialName("envelope")
    data class Deliver(val envelope: Envelope) : ServerFrame

    /** The queue has been drained; everything after this is live. */
    @Serializable
    @SerialName("synced")
    data object Synced : ServerFrame

    /** A new post in a public space you are a member of. */
    @Serializable
    @SerialName("post")
    data class SpacePost(val post: Post) : ServerFrame

    /** A post was edited or removed (by its author or a moderator); [post] is null when removed. */
    @Serializable
    @SerialName("post_changed")
    data class SpacePostChanged(val space: String, val id: Long, val post: Post? = null) : ServerFrame

    /** One-time keys are running low and should be topped up. */
    @Serializable
    @SerialName("keys_low")
    data class KeysLow(val preKeys: Int, val kyberPreKeys: Int) : ServerFrame
}

/** Frames from the client on the WebSocket. */
@Serializable
sealed interface ClientFrame {
    /** The envelope has been stored on the device and may be deleted from the server. */
    @Serializable
    @SerialName("ack")
    data class Ack(val serverId: Long) : ClientFrame
}
