package app.blesk.core.data

import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Presence
import app.blesk.core.data.model.StoredMedia
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.File

/** The state of the link to the server, as the status capsule shows it. */
enum class Connection {
    Online,

    /** The phone has no network at all. */
    NoNetwork,
    Connecting,

    /** Connected and taking in what arrived while away. */
    Updating,

    /** The network is there, but the server does not answer. */
    Unreachable,
    ;

    /** Messages written now leave right away. */
    val canSend: Boolean get() = this == Online || this == Updating
}

/** Everything the screens need from a world of chats, whether it is the demo or a real server. */
interface ChatRepository : ChatTools, PeopleControls, TransferControls {
    /** Conversations shown in the list; room channels live inside their rooms. */
    val conversations: Flow<List<Conversation>>

    /** Conversations where someone is typing right now. */
    val typing: StateFlow<Set<String>>

    /** People you can write to directly: those you opened a chat with or wrote to, and did not block. */
    val contacts: Flow<List<Contact>>

    fun conversation(id: String): Flow<Conversation?>

    fun messages(conversationId: String): Flow<List<Message>>

    /** Every song sent in any chat, newest first: the music library. */
    val music: Flow<List<Message>>

    /** A cached contact name for message authors; null when unknown. */
    fun contact(id: String): Contact?

    /** The direct chat with [username], creating it if needed; null if there is no such person. */
    /** Opens (or creates) the chat with [username]; [link] is the key from their link or QR code, for hidden accounts. */
    suspend fun openDirect(username: String, link: String? = null): String?

    fun send(conversationId: String, text: String, replyToId: String? = null)

    fun sendAttachment(conversationId: String, attachment: Attachment, caption: String = "", replyToId: String? = null)

    fun sendTheme(conversationId: String, theme: ThemeSpec, caption: String) =
        sendAttachment(conversationId, Attachment.Theme(theme.normalized()), caption)

    fun toggleReaction(conversationId: String, messageId: String, symbol: String)

    fun edit(conversationId: String, messageId: String, text: String)

    /** Deletes for everyone. Only your own messages can be deleted. */
    fun delete(conversationId: String, messageId: String)

    fun markRead(conversationId: String)

    /** Messages containing [query], newest first. Runs on the phone only. */
    suspend fun search(query: String): List<Message>

    /** How the link to the server is doing; the demo is always online. */
    val connection: Flow<Connection> get() = flowOf(Connection.Online)

    /** Tries to reach the server right away instead of waiting for the next attempt. */
    fun reconnect() = Unit

    /** Sends a message that failed to go out once more, as a new message at the end of the chat. */
    fun retry(conversationId: String, messageId: String) = Unit

    /** Tells the other side you are typing; a no-op where it makes no sense. */
    fun setTyping(conversationId: String, active: Boolean) = Unit
}

/** Files on their way between this phone and the server. */
interface TransferControls {
    /** Files being sent or received, by message id (and `id/index` for album items). */
    val transfers: StateFlow<Map<String, Transfer>> get() = MutableStateFlow(emptyMap())

    /** Stops a file on its way: an upload drops the unsent message, a download can be started again. */
    fun cancelTransfer(conversationId: String, messageId: String) = Unit

    /** Fetches the files of a message again, from where the last attempt stopped. */
    fun retryDownload(conversationId: String, messageId: String) = Unit

    /** A video of a message that can play before it has fully arrived; starts its download. Null if it cannot. */
    suspend fun stream(conversationId: String, messageId: String, index: Int): StreamSource? = null
}

/** How many chats can be pinned at once; the list stays a list, not a second list on top. */
const val MAX_PINNED = 10

/** Who can reach you: being found by name, and blocking. */
interface PeopleControls {
    /** People you blocked. */
    val blocked: Flow<List<Contact>> get() = flowOf(emptyList())

    /** Blocks or unblocks [username]. A blocked person's messages and calls are dropped on this phone without a trace. */
    fun setBlocked(username: String, blocked: Boolean) = Unit

    /** The key your own profile link carries, so it finds you even when you hide from search; null in the demo. */
    suspend fun linkKey(): String? = null

    /** Whether people may find you by typing your username. */
    suspend fun setDiscoverable(discoverable: Boolean) = Unit

    /** What people allowed to tell you have said about being online, by username. */
    val presence: StateFlow<Map<String, Presence>> get() = MutableStateFlow(emptyMap())

    /** The app came to the front or went away; online status follows it. */
    fun setForeground(foreground: Boolean) = Unit

    /** Accepts a message request: the person becomes a contact and sees read receipts and typing again. */
    fun accept(username: String) = Unit

    /** Your profile changed: it goes again to the people you talk to. */
    fun profileChanged() = Unit

    /** Makes a contact a stranger again: the chat stays, but they get no receipts or typing, as with a request. */
    fun removeContact(username: String) = Unit

    /** Deletes your account on the server; false if the server could not be reached. True in the demo, where there is none. */
    suspend fun deleteAccount(): Boolean = true
}

/** Things you do to a chat besides writing in it. */
interface ChatTools {
    /** Keeps what you typed but did not send. */
    fun saveDraft(conversationId: String, text: String) = Unit

    /** Archived chats leave the list for the archive. */
    fun setArchived(conversationId: String, archived: Boolean) = Unit

    /** Silent until [untilMillis]; [Long.MAX_VALUE] for good, 0 to turn sound back on. */
    fun mute(conversationId: String, untilMillis: Long) = Unit

    /** Shows the chat as unread again, or clears that mark. */
    fun markUnread(conversationId: String, unread: Boolean) = Unit

    /** Removes a one-to-one chat and its messages from this phone only. */
    fun deleteChat(conversationId: String) = Unit

    /** Pinned chats stay on top of the list, newly pinned ones last; at most [MAX_PINNED]. */
    fun setChatPinned(conversationId: String, pinned: Boolean) = Unit

    /** Puts the pinned chats in the order of [ids]. */
    fun reorderPinned(ids: List<String>) = Unit

    /** One pinned message per chat, shown above the messages; null unpins. Kept on this phone. */
    fun pinMessage(conversationId: String, messageId: String, pinned: Boolean) = Unit

    /** Disappearing messages for everyone in the chat; 0 turns them off. */
    fun setTimer(conversationId: String, seconds: Int) = Unit

    /** Sends a copy of a message to other chats, marked as forwarded. */
    fun forward(conversationId: String, messageId: String, to: List<String>) = Unit

    /** Attachments whose files are on this phone. */
    suspend fun storedMedia(): List<StoredMedia> = emptyList()

    /**
     * Deletes the files of attachments sent before [beforeMillis], in one chat or in all; the
     * messages stay, and so do the files of the messages in [keep].
     */
    suspend fun clearMedia(conversationId: String?, beforeMillis: Long, keep: Set<String> = emptySet()) = Unit
}

/** A file on its way: [done] of [total] bytes; [total] is -1 while unknown. */
data class Transfer(val done: Long, val total: Long, val failed: Boolean = false) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** Where a video still on its way can be played from: its growing ciphertext, the key and the final size. */
class StreamSource(val ciphertext: File, val key: ByteArray, val ciphertextBytes: Long)
