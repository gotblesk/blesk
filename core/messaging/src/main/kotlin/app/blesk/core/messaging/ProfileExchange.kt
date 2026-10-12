package app.blesk.core.messaging

import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ContactEntity
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.settings.Audience
import app.blesk.core.media.MediaVault
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.AttachmentPointer
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.OutgoingMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Profiles between people: yours goes out when a session starts and to every contact when it
 * changes; theirs is kept with the contact. The photo travels as an encrypted attachment and
 * only to those [photoAudience] lets see it.
 */
internal class ProfileExchange(
    private val db: BleskDatabase,
    private val api: BleskApi,
    private val crypto: MessageCrypto,
    private val media: MediaVault,
    private val me: String,
    private val profile: () -> OwnProfile,
    private val photoAudience: () -> Audience,
    private val allowed: suspend (username: String, audience: Audience) -> Boolean,
    private val enqueue: suspend (recipient: String, content: Content) -> Unit,
) {
    /** Sent straight away, ahead of the first message of a new session. */
    suspend fun share(recipient: String) {
        val own = profile()
        if (own.isEmpty) return
        val photo = if (allowed(recipient, photoAudience())) upload(own) else null
        val content = Content(UUID.randomUUID().toString(), System.currentTimeMillis(), body(own, photo))
        val sealed = withContext(Dispatchers.IO) { crypto.encrypt(recipient, content) }
        api.send(recipient, OutgoingMessage(content.id, sealed.type, sealed.body))
    }

    /** Your changed profile to every contact, with the photo uploaded once for all of them. */
    suspend fun broadcast(clock: () -> Long) {
        val own = profile()
        val people = db.contacts().all().filter { it.added && !it.blocked && it.username != me }
        if (people.isEmpty()) return
        val photo = if (photoAudience() != Audience.Nobody) upload(own) else null
        people.forEach { contact -> enqueue(contact.username, Content(UUID.randomUUID().toString(), clock(), body(own, photo))) }
    }

    suspend fun receive(sender: String, body: Body.Profile) {
        val base = db.contacts().get(sender) ?: ContactEntity(sender, sender, added = false)
        val links = body.links.map { it.trim().take(Body.Profile.MAX_LINK) }.filter { it.isNotEmpty() }.take(Body.Profile.MAX_LINKS)
        // A new photo replaces the old one; none means they no longer show it to you.
        val photo = body.photo?.takeIf { it.kind == Attachments.KIND_PHOTO }?.let { pointer ->
            runCatching { withContext(Dispatchers.IO) { Attachments.download(api, media, pointer) } }.getOrNull() as? Attachment.Photo
        }
        if (body.photo == null || photo != null) base.photo?.let { media.forget(Attachment.Photo(it, 0, 0)) }
        val updated = base.copy(
            name = body.name.take(MAX_NAME),
            bio = body.bio.take(MAX_BIO),
            links = links.joinToString("\n"),
            photo = if (body.photo == null) null else photo?.path ?: base.photo,
        )
        db.contacts().upsert(updated)
        db.conversations().get(sender)?.let { db.conversations().upsert(it.copy(title = body.name.ifBlank { sender })) }
    }

    private fun body(own: OwnProfile, photo: AttachmentPointer?) = Body.Profile(own.name, own.bio, own.links, photo)

    private suspend fun upload(own: OwnProfile): AttachmentPointer? {
        val file = own.photo?.takeIf { it.exists() } ?: return null
        return runCatching { withContext(Dispatchers.IO) { Attachments.upload(api, Attachment.Photo(file.path, 0, 0)) } }.getOrNull()
    }

    private companion object {
        const val MAX_NAME = 64
        const val MAX_BIO = 160
    }
}
