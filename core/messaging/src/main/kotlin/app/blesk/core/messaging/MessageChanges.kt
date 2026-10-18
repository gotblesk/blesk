package app.blesk.core.messaging

import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.model.Attachment
import app.blesk.core.media.MediaVault
import app.blesk.shared.protocol.Body

/**
 * What changes messages already on this phone, from the person who sent them: a reaction, an
 * edit, a deletion, or the whole one-to-one chat cleared for both. Also keeps the chat list's
 * last line in step with what is left.
 */
internal class MessageChanges(
    private val db: BleskDatabase,
    private val media: MediaVault,
    private val labels: (Attachment?) -> String,
) {
    suspend fun apply(sender: String, body: Body) {
        when (body) {
            is Body.Reaction -> db.messages().changeReactions(body.target) {
                encodeReactions(applyReaction(decodeReactions(it), sender, body.symbol, body.remove))
            }
            is Body.Edit -> if (db.messages().editText(body.target, sender, body.text) > 0) refreshPreview(sender)
            is Body.Delete -> db.messages().get(body.target)?.takeIf { it.authorId == sender }?.let {
                db.messages().delete(it.id)
                refreshPreview(sender)
            }
            is Body.ClearChat -> if (db.conversations().get(sender) != null) clear(sender)
            else -> Unit
        }
    }

    /** The chat and its messages gone from this phone, with the files they brought. */
    suspend fun clear(conversationId: String) {
        db.messages().withFilesIn(conversationId).forEach { media.forget(decodeAttachment(it.attachment)) }
        db.messages().deleteConversation(conversationId)
        db.conversations().deleteWithChildren(conversationId)
    }

    suspend fun refreshPreview(conversationId: String) {
        val last = db.messages().last(conversationId)
        val text = last?.let { it.text.ifEmpty { labels(decodeAttachment(it.attachment)) } }
        db.conversations().setPreview(conversationId, null, text ?: "", last?.sentAt ?: 0)
    }
}
