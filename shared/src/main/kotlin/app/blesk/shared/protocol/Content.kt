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
    /**
     * For group and room messages: the version of the group's state the sender wrote it in. A phone whose state is
     * older keeps the message until the state that lets it be read arrives, instead of dropping it.
     */
    val groupVersion: Long = 0,
    /** With [groupVersion], the owner's count of that state; null from an app too old to send it. */
    val groupOwnerVersion: Long? = null,
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

    /** The sender deleted the one-to-one chat for both: the receiver's copy of it goes too. */
    @Serializable
    @SerialName("clear_chat")
    data object ClearChat : Body

    /** The new state of a group, sent one-to-one to every member by an admin. */
    @Serializable
    @SerialName("group_update")
    data class GroupUpdate(val state: GroupState) : Body

    /**
     * One of the sender's SenderKeys for a group epoch: the one for everyone in the group, or for the readers of a
     * closed channel. Sent one-to-one, only to those who may read what it encrypts.
     */
    @Serializable
    @SerialName("sender_key")
    data class SenderKey(val groupId: String, val epoch: Int, val distribution: String) : Body

    /**
     * The media key and LiveKit [room] of a closed voice channel, from the room's keeper, its first admin, sent
     * one-to-one only to those who see the channel. A new key, with a higher [version], takes the place of the old one
     * whenever someone who had it no longer sees the channel or the group moves to a new epoch.
     */
    @Serializable
    @SerialName("voice_key")
    data class VoiceKey(val groupId: String, val channelId: String, val version: Long, val room: String, val key: String) : Body

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
    /** 1: AES-CBC with HMAC over the whole file; 2: Tink streaming AEAD in 1 MB segments, playable while it downloads. */
    val scheme: Int = 1,
    /** For music: what the file says it is. */
    val title: String? = null,
    val artist: String? = null,
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
    /** Grows with every change; older states are ignored. Long, so that no run of states can carry it past its end. */
    val version: Long,
    /**
     * Grows with every state the owner makes; everyone else's carries it as it was. A state is newer than another when
     * this is higher, or the same with a higher [version]: the owner's next state follows whatever others sent
     * meanwhile, and one built before it never undoes it.
     */
    val ownerVersion: Long = 0,
    /** Grows when someone leaves or is removed, so everyone moves to fresh sender keys. */
    val epoch: Int,
    val members: List<GroupMember>,
    val channels: List<GroupChannel> = emptyList(),
    /** Current invite code; null when invites are off. Only members ever see it. */
    val invite: String? = null,
    /** Base64 media key for the voice channels open to all; replaced with every new epoch. Closed ones have their own. */
    val voiceKey: String? = null,
    val description: String = "",
    /** Header hue, one of the palette's; null for the default light. */
    val hue: Int? = null,
    /** What every member may do; admins and the owner may do more. */
    val memberRights: Set<Right> = Right.MEMBER_DEFAULT,
    /** Room roles, highest first. */
    val roles: List<RoomRole> = emptyList(),
    val invites: List<GroupInvite> = emptyList(),
    /** Removed for good: they cannot come back by an invite. */
    val banned: List<String> = emptyList(),
    /** What admins did, newest last; seen by those with [Right.Journal]. */
    val log: List<LogEntry> = emptyList(),
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
data class GroupMember(
    val username: String,
    val role: GroupRole,
    /** An admin's rights; null gives an admin all of [Right.ADMIN_ALL]. */
    val rights: Set<Right>? = null,
    /** Ids of the room roles this member has. */
    val roles: List<String> = emptyList(),
)

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
    val description: String = "",
    /** Room role ids (or [GroupPolicy.EVERYONE]) this channel is hidden from. */
    val hiddenFrom: Set<String> = emptySet(),
)

/**
 * An invite into a closed group or room, `https://blesk.su/r/<code>?g=<group>&a=<admin>`, handed to
 * people you want to invite: joining asks that admin. The older `blesk://join?g=&a=&c=` still works.
 */
object InviteLink {
    private const val LEGACY = "blesk://join"
    private val groupPattern = Regex("^[A-Za-z0-9-]{8,64}$")
    private val codePattern = Regex("^[A-Za-z0-9_-]{8,64}$")

    fun format(groupId: String, admin: String, code: String) = "${BleskLinks.SITE}/r/$code?g=$groupId&a=$admin"

    data class Parsed(val groupId: String, val admin: String, val code: String)

    fun parse(link: String): Parsed? {
        val (code, params) = BleskLinks.site(link, "/r/")?.let { it.rest to it.params }
            ?: BleskLinks.legacy(link, LEGACY)?.takeIf { it.rest.isEmpty() }?.let { it.params["c"] to it.params }
            ?: return null
        val group = params["g"]?.takeIf(groupPattern::matches) ?: return null
        val admin = params["a"]?.takeIf(Usernames::isValid) ?: return null
        val valid = code?.takeIf(codePattern::matches) ?: return null
        return Parsed(group, admin, valid)
    }
}

/**
 * A profile link or QR code, `https://blesk.su/@name`, with the link key (see [Api.linkKey]) when
 * the account hides from search, so the link still finds it. The older `blesk://u/name` still works.
 */
object ProfileLink {
    private const val LEGACY = "blesk://u/"
    private val keyPattern = Regex("^[A-Za-z0-9_-]{8,64}$")

    fun format(username: String, key: String?) = "${BleskLinks.SITE}/@$username" + (key?.let { "?k=$it" } ?: "")

    data class Parsed(val username: String, val key: String?)

    fun parse(link: String): Parsed? {
        val parts = BleskLinks.site(link, "/@") ?: BleskLinks.legacy(link, LEGACY) ?: return null
        val username = parts.rest.lowercase().takeIf(Usernames::isValid) ?: return null
        return Parsed(username, parts.params["k"]?.takeIf(keyPattern::matches))
    }
}

/** A public channel or open room, `https://blesk.su/c/<handle>`: neither is encrypted, so the link is no secret. */
object ChannelLink {
    fun format(handle: String) = "${BleskLinks.SITE}/c/$handle"

    /** The handle the link points at. */
    fun parse(link: String): String? = BleskLinks.site(link, "/c/")?.rest?.lowercase()?.takeIf(Spaces::isValidHandle)
}

/** A link cut after its known start: what follows up to the query, and the query's parameters. */
internal class LinkParts(val rest: String, val params: Map<String, String>)

/**
 * Reads blesk's links: the site's `https://blesk.su/...`, also typed without the scheme or with
 * `www.`, and the older `blesk://...`. Any other host, `blesk.su.example.com` too, is not blesk.
 */
internal object BleskLinks {
    const val SITE = "https://blesk.su"

    private val sitePattern =
        Regex("^(?:https?://)?(?:www\\.)?blesk\\.su(/[^?#]*)(?:\\?([^#]*))?(?:#.*)?$", RegexOption.IGNORE_CASE)

    /** A site link whose path starts with [path]; some apps send the `@` of a profile path escaped. */
    fun site(link: String, path: String): LinkParts? {
        val match = sitePattern.matchEntire(link.trim()) ?: return null
        val full = match.groupValues[1].replace("%40", "@")
        if (!full.startsWith(path)) return null
        return LinkParts(full.removePrefix(path).removeSuffix("/"), params(match.groupValues[2]))
    }

    /** An old `blesk://` link that starts with [prefix]. */
    fun legacy(link: String, prefix: String): LinkParts? {
        val text = link.trim()
        if (!text.startsWith(prefix)) return null
        val rest = text.removePrefix(prefix)
        return LinkParts(rest.substringBefore('?'), params(rest.substringAfter('?', "")))
    }

    private fun params(query: String): Map<String, String> =
        query.split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=', "") }
}
