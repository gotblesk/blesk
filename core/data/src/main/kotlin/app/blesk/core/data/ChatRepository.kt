package app.blesk.core.data

import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.Message
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Everything the screens need from a world of chats, whether it is the demo or a real server. */
interface ChatRepository {
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

    /** Tells the other side you are typing; a no-op where it makes no sense. */
    fun setTyping(conversationId: String, active: Boolean) = Unit
}
