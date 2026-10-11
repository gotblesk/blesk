package app.blesk.core.data.model

import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import java.time.Instant

/** How a conversation is protected. Drives the glint: only [EndToEnd] shines. */
enum class Protection { EndToEnd, Open }

enum class ConversationKind { Direct, Group, Room, Channel, Bot }

data class Contact(val id: String, val name: String, val username: String)

data class Preview(val authorName: String?, val text: String)

data class Conversation(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val protection: Protection,
    val preview: Preview?,
    val updatedAt: Instant,
    val unread: Int = 0,
    val pinned: Boolean = false,
    val memberCount: Int = 0,
    val channels: ImmutableList<RoomChannel> = persistentListOf(),
    /** For a room channel: the room it belongs to. Channels are not listed on their own. */
    val parentId: String? = null,
)

enum class RoomChannelKind { Text, Voice }

data class RoomChannel(
    val id: String,
    val title: String,
    val kind: RoomChannelKind,
    val unread: Int = 0,
    val participants: ImmutableList<Contact> = persistentListOf(),
    val speaking: ImmutableSet<String> = persistentSetOf(),
)

enum class SendStatus { Sending, Sent, Delivered, Read }

data class Message(
    val id: String,
    val conversationId: String,
    val authorId: String,
    val text: String,
    val sentAt: Instant,
    val status: SendStatus = SendStatus.Read,
    val replyToId: String? = null,
    val reactions: ImmutableList<Reaction> = persistentListOf(),
    val edited: Boolean = false,
    val attachment: Attachment? = null,
) {
    val isOwn: Boolean get() = authorId == ME
}

/** Something carried by a message besides its text. Paths point into the app's private storage. */
sealed interface Attachment {
    /** A theme someone shared; anyone in the chat can apply it. */
    data class Theme(val spec: ThemeSpec) : Attachment

    data class Photo(val path: String, val width: Int, val height: Int) : Attachment

    data class Video(val path: String, val thumbnail: String, val width: Int, val height: Int, val durationMillis: Long) : Attachment

    data class File(val path: String, val name: String, val sizeBytes: Long, val mime: String) : Attachment

    /** A voice note; [waveform] is the bar heights encoded by the media module. */
    data class Voice(val path: String, val durationMillis: Long, val waveform: String) : Attachment

    /** A round video message recorded with the front camera. */
    data class Circle(val path: String, val poster: String, val durationMillis: Long) : Attachment
}

/** A reaction and who left it. [mine] is whether the phone owner is among them. */
data class Reaction(val symbol: String, val count: Int, val mine: Boolean)

const val ME = "me"
