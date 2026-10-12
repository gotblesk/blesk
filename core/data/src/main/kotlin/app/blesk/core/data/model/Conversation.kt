package app.blesk.core.data.model

import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.LinkPreview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
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

data class Contact(
    val id: String,
    val name: String,
    val username: String,
    /** Their profile photo on this phone, if they shared it with you. */
    val photo: String? = null,
    val bio: String = "",
    val links: ImmutableList<String> = persistentListOf(),
)

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
    /** Up to three members other than you, for a group's avatar of their monograms. */
    val faces: ImmutableList<Contact> = persistentListOf(),
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
    /** Someone you do not know wrote first: it waits in «Запросы» until you accept, delete or block. */
    val request: Boolean = false,
    /** The other person's profile photo on this phone, for a direct chat. */
    val photo: String? = null,
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
    /** The card of the first link in the text, as the sender's phone built it. */
    val link: LinkPreview? = null,
    /** Its files are still on the server: not downloaded yet, or waiting for a tap. */
    val pending: Boolean = false,
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
    data class Photo(
        val path: String,
        val width: Int,
        val height: Int,
        /** A tiny blurred JPEG in Base64, shown while the photo itself is still on its way. */
        val preview: String = "",
        /** How much it is to download, before it is here. */
        val sizeBytes: Long = 0,
    ) : Attachment

    @Serializable
    @SerialName("video")
    data class Video(
        val path: String,
        val thumbnail: String,
        val width: Int,
        val height: Int,
        val durationMillis: Long,
        /** A small poster in Base64 for before the video arrives. */
        val preview: String = "",
        val sizeBytes: Long = 0,
    ) : Attachment

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

    /** A song sent as it is, played right in the chat. */
    @Serializable
    @SerialName("music")
    data class Music(
        val path: String,
        val name: String,
        val title: String,
        val artist: String,
        val durationMillis: Long,
        val sizeBytes: Long,
        val mime: String,
    ) : Attachment

    /** A sticker: no bubble, a transparent background; [emoji] is what it stands for. */
    @Serializable
    @SerialName("sticker")
    data class Sticker(val path: String, val emoji: String, val format: StickerFormat) : Attachment

    /** Up to [MAX] photos and videos sent together, shown as one mosaic. */
    @Serializable
    @SerialName("album")
    data class Album(val items: List<Attachment>) : Attachment {
        companion object {
            const val MAX = 10
        }
    }
}

/** The photos and videos an attachment shows, one by one: an album's items, or the attachment itself. */
val Attachment.visuals: List<Attachment>
    get() = when (this) {
        is Attachment.Album -> items.filter { it is Attachment.Photo || it is Attachment.Video }
        is Attachment.Photo, is Attachment.Video -> listOf(this)
        else -> emptyList()
    }

/** The same attachment without its files on this phone, or null when it has none to remove. */
fun Attachment.withoutFiles(): Attachment? = when (this) {
    is Attachment.Photo -> copy(path = "").takeIf { path.isNotEmpty() }
    is Attachment.Video -> copy(path = "", thumbnail = "").takeIf { path.isNotEmpty() || thumbnail.isNotEmpty() }
    is Attachment.File -> copy(path = "").takeIf { path.isNotEmpty() }
    is Attachment.Voice -> copy(path = "").takeIf { path.isNotEmpty() }
    is Attachment.Circle -> copy(path = "", poster = "").takeIf { path.isNotEmpty() || poster.isNotEmpty() }
    is Attachment.Music -> copy(path = "").takeIf { path.isNotEmpty() }
    is Attachment.Sticker -> copy(path = "").takeIf { path.isNotEmpty() }
    is Attachment.Theme -> null
    is Attachment.Album -> Attachment.Album(items.map { it.withoutFiles() ?: it }).takeIf { files.isNotEmpty() }
}

/** Paths of the files an attachment keeps on this phone. */
val Attachment.files: List<String>
    get() = when (this) {
        is Attachment.Photo -> listOf(path)
        is Attachment.Video -> listOf(path, thumbnail)
        is Attachment.File -> listOf(path)
        is Attachment.Voice -> listOf(path)
        is Attachment.Circle -> listOf(path, poster)
        is Attachment.Music -> listOf(path)
        is Attachment.Sticker -> listOf(path)
        is Attachment.Theme -> emptyList()
        is Attachment.Album -> items.flatMap { it.files }
    }.filter { it.isNotEmpty() }

/** An attachment kept on this phone, for counting space by chat and by kind. */
data class StoredMedia(val conversationId: String, val sentAtMillis: Long, val attachment: Attachment)

/** A reaction and who left it. [mine] is whether the phone owner is among them. */
data class Reaction(val symbol: String, val count: Int, val mine: Boolean)

/** What someone last told you about being online; [lastSeen] is when they went away. */
data class Presence(val online: Boolean, val lastSeen: Instant?, val heardAt: Instant) {
    /** Online only while fresh: a phone that drops off the network cannot say goodbye. */
    fun isOnline(now: Instant): Boolean = online && Duration.between(heardAt, now) < STALE

    /** Seen within a day: shown as «недавно в сети», never as an exact time. */
    fun wasRecent(now: Instant): Boolean {
        val last = if (online) heardAt else lastSeen ?: return false
        return Duration.between(last, now) < RECENT
    }

    companion object {
        val STALE: Duration = Duration.ofMinutes(5)
        val RECENT: Duration = Duration.ofDays(1)
    }
}

/**
 * The house reaction «блик», set by a double tap: a token rather than an emoji, drawn as a spark
 * of the theme's light. Older versions show the token as text, which is harmless.
 */
const val GLINT_REACTION = "blesk:glint"

const val ME = "me"
