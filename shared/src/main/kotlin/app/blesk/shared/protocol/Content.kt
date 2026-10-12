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
    /** For group and room messages: the conversation inside the group (the group id, or `group:channel`). */
    val group: String? = null,
    /** The message disappears this many seconds after [sentAtMillis] on every phone; 0 keeps it. */
    val expiresInSeconds: Int = 0,
)

/**
 * A link card. The sender's phone fetches the page, so neither the server nor the recipients
 * visit it; [image] is a small JPEG in Base64.
 */
@Serializable
data class LinkPreview(
    val url: String,
    val title: String,
    val description: String = "",
    val image: String? = null,
) {
    /** What a recipient keeps: only a card for a link that is really in [text], within the limits. */
    fun sanitized(text: String): LinkPreview? {
        if (!url.startsWith("https://", ignoreCase = true) || url !in text || title.isBlank()) return null
        return LinkPreview(
            url = url,
            title = title.take(MAX_TITLE),
            description = description.take(MAX_DESCRIPTION),
            image = image?.takeIf { it.length <= MAX_IMAGE_BYTES * 4 / 3 + 4 },
        )
    }

    companion object {
        const val MAX_TITLE = 200
        const val MAX_DESCRIPTION = 300
        const val MAX_IMAGE_BYTES = 48 * 1024
    }
}

@Serializable
sealed interface Body {
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        val replyTo: String? = null,
        val attachment: AttachmentPointer? = null,
        /** Passed on from another chat; who wrote it first is not revealed. */
        val forwarded: Boolean = false,
        /** The card of the first link in [text], built by the sender's phone. */
        val link: LinkPreview? = null,
        /** Photos and videos sent together; [attachment] stays empty then. */
        val album: List<AttachmentPointer> = emptyList(),
    ) : Body

    /** The chat's disappearing-message timer was changed by the sender. */
    @Serializable
    @SerialName("timer")
    data class Timer(val seconds: Int) : Body

    @Serializable
    @SerialName("receipt")
    data class Receipt(val kind: ReceiptKind, val ids: List<String>) : Body

    @Serializable
    @SerialName("typing")
    data class Typing(val active: Boolean) : Body

    /**
     * «В сети», sent only to people allowed to see it, end to end like everything else. [reply]
     * marks an answer to someone who just came online, so answers are never answered again.
     */
    @Serializable
    @SerialName("presence")
    data class Presence(val online: Boolean, val lastSeenMillis: Long? = null, val reply: Boolean = false) : Body

    @Serializable
    @SerialName("reaction")
    data class Reaction(val target: String, val symbol: String, val remove: Boolean = false) : Body

    @Serializable
    @SerialName("edit")
    data class Edit(val target: String, val text: String) : Body

    @Serializable
    @SerialName("delete")
    data class Delete(val target: String) : Body

    /** The new state of a group, sent one-to-one to every member by an admin. */
    @Serializable
    @SerialName("group_update")
    data class GroupUpdate(val state: GroupState) : Body

    /** The sender's SenderKey for a group epoch, sent one-to-one so only members can read the group. */
    @Serializable
    @SerialName("sender_key")
    data class SenderKey(val groupId: String, val epoch: Int, val distribution: String) : Body

    /** Asks an admin to be let in with an invite code. */
    @Serializable
    @SerialName("join_request")
    data class JoinRequest(val groupId: String, val code: String) : Body

    /** The sender leaves the group. */
    @Serializable
    @SerialName("group_leave")
    data class GroupLeave(val groupId: String) : Body

    /** Rings the other person. [key] is the Base64 media key of the call. */
    @Serializable
    @SerialName("call_offer")
    data class CallOffer(val callId: String, val video: Boolean, val key: String) : Body

    /** The callee picked up. */
    @Serializable
    @SerialName("call_answer")
    data class CallAnswer(val callId: String) : Body

    /** Either side hangs up, declines or is busy; also cancels ringing. */
    @Serializable
    @SerialName("call_end")
    data class CallEnd(val callId: String, val reason: CallEndReason = CallEndReason.HangUp) : Body

    /**
     * Your name, bio and links, shared only with people you write to, and your photo for those
     * allowed to see it. Sent again to contacts whenever it changes.
     */
    @Serializable
    @SerialName("profile")
    data class Profile(
        val name: String,
        val bio: String,
        val links: List<String> = emptyList(),
        val photo: AttachmentPointer? = null,
    ) : Body {
        companion object {
            const val MAX_LINKS = 3
            const val MAX_LINK = 200
        }
    }
}

@Serializable
enum class CallEndReason {
    @SerialName("hangup")
    HangUp,

    @SerialName("declined")
    Declined,

    @SerialName("busy")
    Busy,

    @SerialName("missed")
    Missed,
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

/**
 * A closed group or room as its members see it. It lives only on members' phones and travels
 * only inside Signal messages; the server never learns who is in a group or what it is called.
 */
@Serializable
data class GroupState(
    val id: String,
    val kind: GroupKind,
    val title: String,
    /** Grows with every change; older states are ignored. */
    val version: Int,
    /** Grows when someone leaves or is removed, so everyone moves to fresh sender keys. */
    val epoch: Int,
    val members: List<GroupMember>,
    val channels: List<GroupChannel> = emptyList(),
    /** Current invite code; null when invites are off. Only members ever see it. */
    val invite: String? = null,
    /** Base64 media key for voice channels; replaced with every new epoch. */
    val voiceKey: String? = null,
) {
    fun roleOf(username: String): GroupRole? = members.firstOrNull { it.username == username }?.role

    fun isAdmin(username: String) = roleOf(username).let { it == GroupRole.Owner || it == GroupRole.Admin }
}

@Serializable
enum class GroupKind {
    @SerialName("group")
    Group,

    @SerialName("room")
    Room,
}

@Serializable
enum class GroupRole {
    @SerialName("owner")
    Owner,

    @SerialName("admin")
    Admin,

    @SerialName("member")
    Member,
}

@Serializable
data class GroupMember(val username: String, val role: GroupRole)

@Serializable
enum class GroupChannelKind {
    @SerialName("text")
    Text,

    @SerialName("voice")
    Voice,
}

@Serializable
data class GroupChannel(
    val id: String,
    val title: String,
    val kind: GroupChannelKind = GroupChannelKind.Text,
    /** Only admins may write here (announcements). */
    val adminsOnly: Boolean = false,
)

/** `blesk://join?g=<group>&a=<admin>&c=<code>` — handed to people you want to invite. */
object InviteLink {
    private const val PREFIX = "blesk://join?"

    fun format(groupId: String, admin: String, code: String) = "${PREFIX}g=$groupId&a=$admin&c=$code"

    data class Parsed(val groupId: String, val admin: String, val code: String)

    fun parse(link: String): Parsed? {
        if (!link.trim().startsWith(PREFIX)) return null
        val params = link.trim().removePrefix(PREFIX).split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val group = params["g"]?.takeIf { it.matches(Regex("^[A-Za-z0-9-]{8,64}$")) } ?: return null
        val admin = params["a"]?.takeIf(Usernames::isValid) ?: return null
        val code = params["c"]?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{8,64}$")) } ?: return null
        return Parsed(group, admin, code)
    }
}

/**
 * A profile link or QR code: `blesk://u/name`, with the link key (see [Api.linkKey]) when the
 * account hides from search, so the link still finds it.
 */
object ProfileLink {
    private const val PREFIX = "blesk://u/"

    fun format(username: String, key: String?) = PREFIX + username + (key?.let { "?k=$it" } ?: "")

    data class Parsed(val username: String, val key: String?)

    fun parse(link: String): Parsed? {
        val text = link.trim()
        if (!text.startsWith(PREFIX)) return null
        val rest = text.removePrefix(PREFIX)
        val username = rest.substringBefore('?').takeIf(Usernames::isValid) ?: return null
        val key = rest.substringAfter("?k=", "").takeIf { it.matches(Regex("^[A-Za-z0-9_-]{8,64}$")) }
        return Parsed(username, key)
    }
}
