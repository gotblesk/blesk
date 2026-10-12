package app.blesk.core.data

import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.Message
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Everything the screens need from a world of chats, whether it is the demo or a real server. */
interface ChatRepository : ChatTools {
    /** Conversations shown in the list; room channels live inside their rooms. */
    val conversations: Flow<List<Conversation>>

    /** Conversations where someone is typing right now. */
    val typing: StateFlow<Set<String>>

    /** People you can write to directly. */
    val contacts: Flow<List<Contact>>

    fun conversation(id: String): Flow<Conversation?>

    fun messages(conversationId: String): Flow<List<Message>>

    /** A cached contact name for message authors; null when unknown. */
    fun contact(id: String): Contact?

    /** The direct chat with [username], creating it if needed; null if there is no such person. */
    suspend fun openDirect(username: String): String?

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

    /** Tells the other side you are typing; a no-op where it makes no sense. */
    fun setTyping(conversationId: String, active: Boolean) = Unit
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

    /** Pinned chats stay on top of the list. */
    fun setChatPinned(conversationId: String, pinned: Boolean) = Unit

    /** One pinned message per chat, shown above the messages; null unpins. Kept on this phone. */
    fun pinMessage(conversationId: String, messageId: String?) = Unit

    /** Disappearing messages for everyone in the chat; 0 turns them off. */
    fun setTimer(conversationId: String, seconds: Int) = Unit

    /** Sends a copy of a message to other chats, marked as forwarded. */
    fun forward(conversationId: String, messageId: String, to: List<String>) = Unit
}
