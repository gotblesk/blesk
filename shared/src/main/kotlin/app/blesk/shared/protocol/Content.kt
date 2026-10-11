package app.blesk.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What goes inside the Signal ciphertext. The server never sees any of this. Every content
 * has a sender-chosen [id] so receipts, reactions and edits can point at it.
 */
@Serializable
data class Content(
    val id: String,
    val sentAtMillis: Long,
    val body: Body,
)

@Serializable
sealed interface Body {
    @Serializable
    @SerialName("text")
    data class Text(val text: String, val replyTo: String? = null, val attachment: AttachmentPointer? = null) : Body

    @Serializable
    @SerialName("receipt")
    data class Receipt(val kind: ReceiptKind, val ids: List<String>) : Body

    @Serializable
    @SerialName("typing")
    data class Typing(val active: Boolean) : Body

    @Serializable
    @SerialName("reaction")
    data class Reaction(val target: String, val symbol: String, val remove: Boolean = false) : Body

    @Serializable
    @SerialName("edit")
    data class Edit(val target: String, val text: String) : Body

    @Serializable
    @SerialName("delete")
    data class Delete(val target: String) : Body

    /** Your name and bio, shared only with people you write to. */
    @Serializable
    @SerialName("profile")
    data class Profile(val name: String, val bio: String) : Body
}

@Serializable
enum class ReceiptKind {
    @SerialName("delivered")
    Delivered,

    @SerialName("read")
    Read,
}

/**
 * Where an encrypted attachment lies on the server and how to open it. The key never reaches
 * the server: it travels only inside the Signal message.
 */
@Serializable
data class AttachmentPointer(
    val id: String,
    val key: String,
    val kind: String,
    val name: String? = null,
    val mime: String? = null,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationMillis: Long = 0,
    val waveform: String? = null,
    /** Base64 of a small JPEG preview, encrypted along with the message. */
    val thumbnail: String? = null,
)
