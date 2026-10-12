package app.blesk.core.data.model

import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** How a conversation is protected. Drives the glint: only [EndToEnd] shines. */
enum class Protection { EndToEnd, Open }

enum class ConversationKind {
    Direct,
    Group,
    Room,
    Channel,
    Bot,

    /** «Избранное»: notes to yourself, never leave the phone. */
    Saved,
}

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
    /** Place among pinned chats, smallest first. */
    val pinRank: Int = 0,
    val memberCount: Int = 0,
    val channels: ImmutableList<RoomChannel> = persistentListOf(),
    /** For a room channel: the room it belongs to. Channels are not listed on their own. */
    val parentId: String? = null,
    /** What you started typing and left; shown in the list until sent. */
    val draft: String = "",
    /** Messages vanish this many seconds after they are sent; 0 keeps them. */
    val timerSeconds: Int = 0,
    /** Pinned messages, oldest pin first. */
    val pinnedMessageIds: ImmutableList<String> = persistentListOf(),
    val archived: Boolean = false,
    val mutedUntil: Long = 0,
    val markedUnread: Boolean = false,
) {
    fun muted(now: Long): Boolean = mutedUntil > now
}

enum class RoomChannelKind { Text, Voice }

data class RoomChannel(
    val id: String,
    val title: String,
    val kind: RoomChannelKind,
    val unread: Int = 0,
    val participants: ImmutableList<Contact> = persistentListOf(),
    val speaking: ImmutableSet<String> = persistentSetOf(),
)

/** Ordered: a status never moves back. [Failed] means it could not be sent at all. */
enum class SendStatus { Failed, Sending, Sent, Delivered, Read }

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
    /** When it disappears; null keeps it. */
    val expiresAt: Instant? = null,
    /** Who it was forwarded from, if it was. */
    val forwardedFrom: String? = null,
    /** Inline buttons under a bot message, row by row. */
    val buttons: ImmutableList<ImmutableList<BotButton>> = persistentListOf(),
) {
    val isOwn: Boolean get() = authorId == ME
}

/** A button under a bot message: sends [data] back to the bot, or opens [url]. */
@Serializable
data class BotButton(val text: String, val data: String? = null, val url: String? = null)

/** Something carried by a message besides its text. Paths point into the app's private storage. */
@Serializable
sealed interface Attachment {
    /** A theme someone shared; anyone in the chat can apply it. */
    @Serializable
    @SerialName("theme")
    data class Theme(val spec: ThemeSpec) : Attachment

    @Serializable
    @SerialName("photo")
    data class Photo(val path: String, val width: Int, val height: Int) : Attachment

    @Serializable
    @SerialName("video")
    data class Video(val path: String, val thumbnail: String, val width: Int, val height: Int, val durationMillis: Long) : Attachment

    @Serializable
    @SerialName("file")
    data class File(val path: String, val name: String, val sizeBytes: Long, val mime: String) : Attachment

    /** A voice note; [waveform] is the bar heights encoded by the media module. */
    @Serializable
    @SerialName("voice")
    data class Voice(val path: String, val durationMillis: Long, val waveform: String) : Attachment

    /** A round video message recorded with the front camera. */
    @Serializable
    @SerialName("circle")
    data class Circle(val path: String, val poster: String, val durationMillis: Long) : Attachment
}

/** A reaction and who left it. [mine] is whether the phone owner is among them. */
data class Reaction(val symbol: String, val count: Int, val mine: Boolean)

/**
 * The house reaction «блик», set by a double tap: a token rather than an emoji, drawn as a spark
 * of the theme's light. Older versions show the token as text, which is harmless.
 */
const val GLINT_REACTION = "blesk:glint"

const val ME = "me"
